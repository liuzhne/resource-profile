"""Helpers for composing ASGI lifespan context managers."""

from contextlib import AsyncExitStack, asynccontextmanager
from typing import Any, AsyncContextManager, Callable


LifespanFactory = Callable[[Any], AsyncContextManager[Any]]


def chain_lifespans(*factories: LifespanFactory) -> LifespanFactory:
    """Enter lifespan contexts in order and exit them in reverse order."""

    @asynccontextmanager
    async def chained(app: Any):
        async with AsyncExitStack() as stack:
            for factory in factories:
                await stack.enter_async_context(factory(app))
            yield

    return chained
