# Contributing

Thanks for taking the time to improve ArchLens.

## Development Setup

1. Install Java 25 or newer.
2. Install Maven 3.9 or newer.
3. Run `mvn test` to verify the project locally.

## Pull Requests

- Keep changes focused on one concern.
- Add or update tests when behavior changes.
- Run `mvn test` before submitting.
- Describe the motivation, the implementation, and any tradeoffs in the pull request.

## Documentation

Much of the codebase is not yet fully documented, and we are closing that gap
incrementally rather than in one pass. **When you add or modify a public type,
method, or constructor, give it a Javadoc comment if it doesn't already have
one** — document each record component with `@param`, and each method's
parameters and return value with `@param`/`@return`. You don't need to document
public API you didn't touch, but don't leave something you touched undocumented.

## Code Style

The project uses four-space indentation and LF line endings. The repository includes `.editorconfig` and `.gitattributes` so most editors and Git clients can apply those defaults automatically.

