# Pytest Fundamentals - Learning Notes

## Session Date: 2026-03-16
## Context: Learned from reasoning-service/tests/api/eval/test_facts_api.py

## 4 Layers of a Pytest Test File

1. **Imports** - pytest, test client, mocking tools, domain models under test
2. **Test Data (Helpers/Factories)** - functions that build realistic but controlled data objects,
   so you don't repeat construction logic in every test
3. **Fixtures** - pytest's dependency injection system. Functions decorated with `@pytest.fixture`
   that handle setup (and teardown via `yield`)
4. **Test Classes/Functions** - the actual test logic using fixtures from Layer 3

## Key Concepts

### Fixtures = Dependency Injection
- Pytest matches **parameter names** to registered fixture function names
- If fixture A depends on fixture B, pytest resolves B first and shares the same instance
- This means the test and the fixture both reference the **same object**

### `yield` vs `return` in Fixtures
- `yield` = pause the fixture, hand the value to the test, resume after test completes (cleanup)
- `return` = exit immediately, any `with` blocks or cleanup code never runs
- `yield` replaces JUnit's `@BeforeEach` / `@AfterEach` split - setup and teardown in one place

### Decorators (`@`) - Python vs Java
- Java `@Annotations` = metadata/labels, no runtime effect on the method itself
- Python `@decorators` = actively wraps/transforms the function (syntactic sugar for
  `func = decorator(func)`)
- `@pytest.fixture` registers the function in pytest's fixture system

### The "Front Door / Back Door" Pattern
- `client` (TestClient) = front door - sends HTTP requests like a real caller
- `mock_builder` = back door - controls what the endpoint's dependencies return
- `unittest.mock.patch` swaps real dependencies for mocks inside a `with` block
- The test configures the mock, then makes HTTP calls, then asserts on responses

### `assert_called_once()` - Verifying Behavior
- Mocks track how they were called
- `mock.some_method.assert_called_once()` - verify it was called exactly once
- `mock.some_method.call_args` - inspect what arguments were passed
- `mock.some_method.side_effect = RuntimeError(...)` - make the mock raise an exception

## Analogies
- `yield` in a fixture = a **pause button**; `return` = an **eject button**
- TestClient is like Postman - you can't reach into server internals through it
- pytest fixtures are like a dependency graph - resolved bottom-up, shared instances
