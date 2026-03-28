# NovaTerm Testing Strategy
**Date:** March 2026 | **Author:** NovaTerm Research

---

## Inherited Tests (Termux)

19 test files in `core/terminal-emulator/src/test/java/com/termux/terminal/`:
- `TerminalTestCase.java` base: `MockTerminalOutput`, `withTerminalSized()`, `enterString()`, `assertLinesAre()`, `assertInvariants()`
- Covers: CSI, OSC, DEC SET, APC, DCS, cursor, scroll, resize, unicode, WcWidth, ByteQueue, TextStyle, key handling
- Uses JUnit 3 API (extends TestCase) — works with JUnit 4 runner

## Priority 1: Before First Release

1. **Verify Termux tests pass**: `./gradlew :core:terminal-emulator:test`
2. **Security tests**: OSC 52 limits, title sanitization, malformed sequences
3. **Session lifecycle**: create/destroy session, PTY read/write
4. **Setup Turbine + MockK** for ViewModel tests
5. **Basic CI**: GitHub Actions with unit tests only

## Priority 2: Before Beta

6. Instrumented tests for foreground service
7. Bootstrap extraction tests
8. Paparazzi screenshot tests for Compose screens
9. CI with Android emulator
10. Macrobenchmark (startup time)

## Priority 3: Optimization

11. Fuzzing with Jazzer (random bytes -> TerminalEmulator.append())
12. Performance benchmarks with asciinema replay
13. Microbenchmarks for buffer and parser
14. Battery profiling

## Tools

| Tool | Use | Phase |
|---|---|---|
| JUnit 4 | Existing Termux tests | 1 |
| JUnit 5 | New Kotlin tests | 1 |
| Turbine | Flow/StateFlow testing | 1 |
| MockK | Mocking (Kotlin-native) | 1 |
| Paparazzi | Screenshot tests (JVM) | 2 |
| Macrobenchmark | Startup, rendering perf | 2 |
| Jazzer | Fuzzing the VT parser | 3 |
| vttest | Manual VT compatibility | 3 |

## Performance Benchmarking

- Use asciinema recordings replayed into TerminalEmulator.append()
- Measure: processing time, peak memory, render time per frame
- Ghostty approach: 4GB of public asciinema sessions, replay without pauses
- Target: <5ms input-to-screen latency
