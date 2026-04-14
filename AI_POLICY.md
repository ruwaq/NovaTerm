# AI Contribution Policy

NovaTerm is an AI-native terminal — we embrace AI tools in development. However, contributions must meet our quality standards regardless of how they were written.

## Guidelines

### Allowed

- Using AI assistants (Claude, Copilot, Cursor, etc.) to write code
- AI-assisted code review and refactoring
- AI-generated tests (must be verified to actually test what they claim)
- AI-assisted documentation and translations

### Requirements

1. **You must understand every line.** If you can't explain what your code does and why, don't submit it. AI-generated code that the contributor doesn't understand will be rejected.

2. **Disclose AI usage.** Add `Co-Authored-By:` in commit messages when AI wrote significant portions of code. This is for transparency, not judgment.

3. **No AI-generated issues or comments.** Issue reports, bug descriptions, and review comments must be written by humans. AI-generated "fluff" comments waste maintainer time.

4. **Test AI output.** AI tools hallucinate APIs, invent functions, and produce plausible-but-wrong code. Build it. Test it. Run it. If CI fails, don't submit.

5. **No autonomous agents for unsolicited PRs.** Don't point an AI agent at our repo and tell it to "find and fix issues." Contributions must be intentional and reviewed by a human before submission.

## Rationale

We build AI into our terminal. We'd be hypocrites to ban AI in development. But AI is a tool, not a substitute for engineering judgment. The maintainer reviewing your PR shouldn't have to debug AI hallucinations.

## Enforcement

Contributions that appear to be unreviewed AI output (hallucinated APIs, nonsensical tests, boilerplate that doesn't match our codebase) will be closed without review.
