"""Compatibility provenance for the existing Milvus schema.

``source`` is the stored publisher URL or an opaque legacy marker, never the
collection name. Only upsert's documented ``<doc_id>_<NNNN>`` IDs can recover a
document ID; legacy IDs and non-URL sources remain unknown.
"""
import re
from typing import Any, Dict, Optional
from urllib.parse import urlsplit

from app.services.redis_client import cache_get

_UPSERT_CHUNK_ID = re.compile(r"^([A-Za-z0-9_\-]{1,48})_[0-9]{4}$")


def safe_source_url(source: Optional[str]) -> Optional[str]:
    if not source or any(character.isspace() for character in source):
        return None
    try:
        parsed = urlsplit(source)
        if (parsed.scheme not in ("http", "https") or not parsed.hostname
                or parsed.username or parsed.password):
            return None
        return source
    except ValueError:
        return None


def provenance(hit: Dict[str, Any]) -> Dict[str, Optional[str]]:
    source = hit.get("source")
    source = source if isinstance(source, str) and source else None
    chunk_id = hit.get("chunk_id")
    match = _UPSERT_CHUNK_ID.fullmatch(chunk_id) if isinstance(chunk_id, str) else None
    return {
        "source": source,
        "doc_id": match.group(1) if match else None,
        "source_url": safe_source_url(source),
    }


def revision_key(collection_key: str) -> str:
    return f"edu:rag:revision:{collection_key}"


async def corpus_revision(collection_keys: list[str]) -> str:
    """Cache lookup must reflect successful upserts, including metadata repairs."""
    revisions = []
    for key in sorted(collection_keys):
        revisions.append(str(await cache_get(revision_key(key)) or "initial"))
    return "|".join(revisions)
