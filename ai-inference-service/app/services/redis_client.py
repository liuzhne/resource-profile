"""
共享 Redis 异步客户端：
- 单例，懒加载
- 连不通 / 调用失败时返回 None / False，调用方应自行降级到无缓存路径
- 与 Java 服务共用同一 Redis 实例（默认 localhost:6379）
"""
import logging
from typing import Optional

from app.core.config import settings

logger = logging.getLogger(__name__)

# 懒加载：redis 包未安装（如纯逻辑单测环境）时，模块仍可 import，仅 get_redis() 返回 None 降级
_client = None
_unavailable_logged: bool = False


async def get_redis():
    """返回可用的 Redis 客户端。连接异常 / redis 包缺失时返回 None，且只 warn 一次。"""
    global _client, _unavailable_logged
    if _client is not None:
        return _client
    try:
        from redis.asyncio import Redis  # 延迟 import

        client = Redis(
            host=settings.REDIS_HOST,
            port=settings.REDIS_PORT,
            password=settings.REDIS_PASSWORD or None,
            db=settings.REDIS_DB,
            decode_responses=True,
            socket_connect_timeout=2.0,
            socket_timeout=2.0,
        )
        await client.ping()
        _client = client
        logger.info(
            "Redis 连接成功 host=%s port=%s db=%s",
            settings.REDIS_HOST,
            settings.REDIS_PORT,
            settings.REDIS_DB,
        )
        return _client
    except Exception as exc:
        if not _unavailable_logged:
            logger.warning("Redis 不可用，缓存层降级为无缓存路径: %s", exc)
            _unavailable_logged = True
        return None


async def cache_get(key: str) -> Optional[str]:
    client = await get_redis()
    if client is None:
        return None
    try:
        return await client.get(key)
    except Exception as exc:
        logger.debug("Redis GET 失败 key=%s: %s", key, exc)
        return None


async def cache_setex(key: str, ttl: int, value: str) -> bool:
    client = await get_redis()
    if client is None:
        return False
    try:
        await client.setex(key, ttl, value)
        return True
    except Exception as exc:
        logger.debug("Redis SETEX 失败 key=%s: %s", key, exc)
        return False
