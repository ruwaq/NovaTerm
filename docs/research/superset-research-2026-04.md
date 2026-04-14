---
name: Superset Research 2026-04
description: Research on Superset IDE + competitive landscape for NovaTerm Agent Orchestrator
type: project
originSessionId: 168001fc-549a-4fc7-8e4d-2523c75b2688
---
# Superset + AI Agent Research (2026-04-14)

## Superset IDE Architecture (superset.sh)
- Monorepo: Bun + Turborepo, 10+ packages, 10+ apps
- Tech: Electron + React + TailwindCSS + tRPC + Drizzle ORM + SQLite WAL
- Core: Git worktrees for isolation, agent-agnostic orchestration, real-time dashboard
- Terminal: node-pty + xterm.js, persistent sessions, semaphore-limited concurrent spawns
- Key patterns: idempotency keys, streaming subscriptions, worktree path resolution

## Direct Competitors on Android
- AnyClaw: APK with Claude Code + Codex + OpenClaw (no GPU, no Rust)
- CC Pocket: Mobile client for Claude Code/Codex (remote, WebSocket)
- PocketCode: Runs AI agents directly on Android (offline)
- Termux-AI: Termux + AI chat + code generation
- VritraAI: AI-powered command explanation + security

## On-Device AI Stack (Best in 2026)
- Gemma 3n: 15.6 tok/s Snapdragon 8 Gen 2 INT4 — BEST for mobile
- LiteRT: Google runtime for LLM inference (Vulkan/NNAPI/Hexagon) — REPLACE GemmaEngine
- llama.cpp Vulkan: GPU-accelerated GGUF inference on Android
- SKaiNET: Kotlin Multiplatform deep learning
- Llamatik: On-device LLM via Kotlin Multiplatform

## NPU Performance
- Snapdragon 8 Elite: ~1600 tok/s prefill, ~25 decode
- Dimensity 9400/9500: ~1600 prefill, ~20 decode
- Gemma 3n E2B: optimized for Dimensity 9500

## MCP Mobile Ecosystem
- Mobile-MCP: Kotlin framework for Android MCP tools
- Android MCP SDK: Debug builds for AI tools
- Replicant-MCP: Production-grade MCP server for Android

## Android 16/17 AI
- Gemini Nano: On-device offline model
- AICore: Isolated AI environment
- ML Kit GenAI: High-level text/image generation APIs

## Chinese Community Insights
- AutoGLM-Android: Pure phone agents, no PC needed
- Aries-AI: Virtual screen isolation + multi-model
- Termux + Claude Code used in China with wake locks + tmux

## Strategy
- Don't copy AnyClaw — SURPASS them with Rust + GPU + MCP advantage
- Adopt LiteRT to replace current GemmaEngine (NPU acceleration)
- Implement Agent Orchestrator as differentiator (parallel agents on phone)