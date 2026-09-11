import unittest
from contextlib import asynccontextmanager

from app.core.lifespan import chain_lifespans


class ChainLifespansTest(unittest.IsolatedAsyncioTestCase):

    async def test_enters_in_order_and_exits_in_reverse_order(self):
        events = []

        @asynccontextmanager
        async def parent(app):
            events.append(("parent-enter", app))
            yield
            events.append(("parent-exit", app))

        @asynccontextmanager
        async def mounted(app):
            events.append(("mounted-enter", app))
            yield
            events.append(("mounted-exit", app))

        marker = object()
        async with chain_lifespans(parent, mounted)(marker):
            events.append(("body", marker))

        self.assertEqual(
            [name for name, _ in events],
            ["parent-enter", "mounted-enter", "body", "mounted-exit", "parent-exit"],
        )
        self.assertTrue(all(app is marker for _, app in events))


if __name__ == "__main__":
    unittest.main()
