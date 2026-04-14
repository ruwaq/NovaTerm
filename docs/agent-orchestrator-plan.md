# Plan: NovaTerm Agent Orchestrator — Traer Superset a Android

## Contexto

Superset (superset.sh) es un IDE desktop que orquesta 10+ agentes AI en paralelo usando git worktrees aislados. NovaTerm es la terminal Android con Rust core, MCP server, y GPU rendering. El usuario quiere llevar NovaTerm al nivel de Superset: **orquestación de agentes AI en paralelo** desde la terminal Android.

La diferencia clave: Superset corre en desktop con acceso completo al sistema de archivos. NovaTerm corre en Android donde:
- No hay git worktrees nativos (pero sí hay `proot`/chroot)
- Los agentes AI (Claude Code, Codex CLI) se ejecutan DENTRO de la terminal
- El celular es el único dispositivo — no hay laptop de companion
- MCP ya existe como protocolo de control

La ventaja única de NovaTerm: **los agentes corren dentro de la terminal en el teléfono**, no necesitan una máquina separada. Esto es más poderoso que Superset porque el agente tiene acceso directo al entorno Android.

---

## Arquitectura Objetivo: Agent Orchestrator

### Visión
Un dashboard dentro de NovaTerm donde lanzas múltiples agentes AI (Claude Code, Gemini CLI, Aider, OpenCode) en sesiones aisladas, monitoreas su progreso en tiempo real, revisas sus diffs, y apruebas/rechazas cambios — todo desde el teléfono.

### Componentes Clave

#### 1. AgentWorkspace — Sesiones Aisladas por Agente
- Cada agente corre en su propia sesión de terminal con su propio PTY
- Variables de entorno aisladas (`AGENT_NAME`, `WORKSPACE_ID`, `BRANCH_NAME`)
- Directorio de trabajo configurable (home, proyecto específico, o tmp)
- No usamos git worktrees (Android no lo soporta bien) — usamos **directorios aislados** + `GIT_DIR`/`GIT_WORK_TREE` para simularlo

**Archivos nuevos:**
- `core/session/manager/AgentWorkspace.kt` — Gestión del ciclo de vida del workspace
- `core/session/manager/AgentOrchestrator.kt` — Lanzamiento y monitoreo de agentes

**Archivos a modificar:**
- `core/session/manager/AndroidShellProvider.kt` — Agregar soporte para entornos por-agente
- `app/src/main/java/com/novaterm/app/service/TerminalService.kt` — Registro de agentes

#### 2. Agent Dashboard — UI de Monitoreo en Tiempo Real
- Nueva pantalla Compose con cards para cada agente
- Estado: running, idle, error, completed
- Salida reciente (últimas N líneas)
- Indicador de salud (CPU, memoria, tiempo activo)
- Botones: pausar, reanudar, matar, ver diff, abrir sesión

**Archivos nuevos:**
- `feature/agent/ui/AgentDashboardScreen.kt` — Dashboard principal
- `feature/agent/ui/AgentCard.kt` — Card por agente
- `feature/agent/viewmodel/AgentOrchestratorViewModel.kt` — Estado del dashboard

#### 3. Agent Presets — Configuración Pre-hecha
- Claude Code: `claude --dangerously-skip-permissions` con env vars optimizadas
- Gemini CLI: `gemini` con API key configurada
- Aider: `aider --model anthropic/claude-sonnet-4-20250514`
- OpenCode: `opencode` con configuración
- Custom: cualquier comando con nombre y env vars personalizadas

**Archivos nuevos:**
- `core/session/persistence/AgentPreset.kt` — Data class para presets
- `feature/agent/data/AgentPresetRepository.kt` — Persistencia DataStore

**Archivos a modificar:**
- `app/src/main/java/com/novaterm/app/service/TerminalService.kt` — `createPresetSession()` ya existe, extender

#### 4. Difft Viewer — Revisión de Cambios del Agente
- Vista diff integrada tipo `git diff --color-words`
- Parseo de salida `git diff` con coloreado por tipo (+/-/@@)
- Botones: aprobar todo, rechazar, abrir en sesión
- Integración con OSC 133 para marcar bloques de output de agente

**Archivos nuevos:**
- `feature/agent/ui/DiffViewerScreen.kt` — Vista diff
- `feature/agent/data/DiffParser.kt` — Parser de `git diff`

#### 5. Streaming MCP — Control Remoto de Agentes
- Extender el MCP server existente con nuevas herramientas:
  - `agent_list` — Lista workspaces activos
  - `agent_status` — Estado de un workspace
  - `agent_output` — Stream de output reciente
  - `agent_diff` — Diff de cambios del workspace
  - `agent_approve` — Aprobar cambios (git commit)
  - `agent_reject` — Rechazar cambios (git reset)
- WebSocket streaming para monitoreo en tiempo real (extender Ktor existente)

**Archivos a modificar:**
- `core/mcp/McpServer.kt` — Agregar rutas de agente
- `core/mcp/tool/builtin/` — Nuevas herramientas MCP

#### 6. Session Grouping — Agrupación de Sesiones por Proyecto
- Los tabs de sesión se agrupan por proyecto/workspace
- Expand/collapse por grupo
- Indicador visual de qué agente está corriendo en cada tab

**Archivos a modificar:**
- `app/src/main/java/com/novaterm/app/ui/viewmodel/TerminalViewModel.kt` — Agrupación de sesiones
- `feature/terminal/ui/TerminalScreen.kt` — UI de tabs agrupados

---

## Plan de Implementación por Fases

### Fase 1: AgentWorkspace + Presets — COMPLETADA
**Objetivo**: Lanzar múltiples agentes AI en sesiones aisladas desde NovaTerm

1. ✅ Crear `AgentWorkspace.kt` — modelo de workspace aislado
2. ✅ Crear `AgentOrchestrator.kt` — lógica de lanzamiento paralelo
3. ✅ Extender `AndroidShellProvider.kt` — entornos por-agente
4. ✅ Crear `AgentPreset.kt` + `AgentPresetRepository.kt` — presets para Claude/Gemini/Aider
5. ✅ Extender `TerminalService` — `createAgentSession()` con workspace aislado
6. ✅ UI: `AgentPresetSheet` bottom sheet + `AgentLaunchButton` en `ExtraKeysBar`

### Fase 2: Agent Dashboard (2-3 semanas)
**Objetivo**: Monitorear agentes en tiempo real desde la app

1. Crear `AgentOrchestratorViewModel.kt` — estado de todos los workspaces
2. Crear `AgentDashboardScreen.kt` — Compose UI con cards
3. Crear `AgentCard.kt` — card individual con estado, output, acciones
4. Integrar con `TerminalService` — suscripción a estado de agentes
5. Navigation: nuevo tab "Agents" en la barra inferior

### Fase 3: Diff Viewer (1-2 semanas)
**Objetivo**: Ver y aprobar/rechazar cambios de agentes

1. Crear `DiffParser.kt` — parseo de `git diff --color-words`
2. Crear `DiffViewerScreen.kt` — vista diff con syntax highlighting
3. Acciones: aprobar (commit), rechazar (reset), abrir en terminal
4. Integración con `SemanticZoneTracker` — marcar bloques de agente

### Fase 4: Streaming MCP (2-3 semanas)
**Objetivo**: Control remoto de agentes vía MCP

1. Agregar herramientas MCP: `agent_list`, `agent_status`, `agent_output`, `agent_diff`, `agent_approve`, `agent_reject`
2. Extender Ktor server con WebSocket para streaming
3. Integración con Claude Code (MCP client → NovaTerm MCP server)
4. Documentación de API

### Fase 5: Session Grouping (1 semana)
**Objetivo**: Organizar sesiones por proyecto/agente

1. Modelo de `SessionGroup` — agrupación de sesiones
2. Modificar `TerminalViewModel` — soporte de grupos
3. UI: tabs agrupados con expand/collapse
4. Persistencia de grupos en `SessionStore`

---

## Verificación

### Fase 1
- Lanzar 3+ agentes (Claude, Gemini, custom) simultáneamente
- Verificar que cada agente tiene su propio PTY y directorio de trabajo
- Verificar que los presets persisten entre reinicios

### Fase 2
- Dashboard muestra estado de cada agente en tiempo real
- Los indicadores de salud se actualizan cada 1s
- Botones de pausar/reanudar/matar funcionan correctamente

### Fase 3
- `git diff` se parsea y muestra con colores
- Aprobar hace `git commit`, rechazar hace `git checkout .`
- El diff viewer funciona con output de cualquier agente

### Fase 4
- `claude mcp add novaterm` conecta exitosamente
- Las herramientas MCP responden con estado de agentes
- Streaming funciona sin bloquear la UI

### Fase 5
- Las sesiones se agrupan visualmente por proyecto
- Expand/collapse funciona con gestos de swipe
- Los grupos persisten entre reinicios de la app