FROM python:3.10-slim

WORKDIR /app

# 安装编译依赖（部分 Python 包需要）
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖并安装
# 走国内镜像源：默认 PyPI 在国内拉包极慢（曾出现 ~7 kB/s），叠加 langfuse 依赖回溯
# 会让本步耗时数小时。镜像源可经 build-arg 覆盖（海外构建可传 https://pypi.org/simple）。
# ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# RUN pip install --no-cache-dir -i "${PIP_INDEX_URL}" -r requirements.txt

# 复制应用代码与运维脚本
COPY app ./app
COPY scripts ./scripts

# 非 root 运行（安全）
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# 暴露端口
EXPOSE 8090

# 启动（单 worker，支持 Render $PORT 动态绑定，默认 8090）
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8090} --workers 1"]