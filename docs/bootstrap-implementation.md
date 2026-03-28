# NovaTerm Bootstrap System Implementation Guide
**Date:** March 2026 | **Author:** NovaTerm Research

---

## How Termux Bootstrap Works

### Build Time
1. `generate-bootstraps.sh` downloads `.deb` packages from apt repo
2. Extracts packages, collects symlinks into `SYMLINKS.txt`
3. Generates ZIP with compression level 9
4. ZIP is embedded as `libtermux-bootstrap.so` in `lib/<arch>/` via assembler (`.rodata` section)
5. SHA-256 checksums verified during Gradle build

### First Run
1. `TermuxInstaller.setupBootstrapIfNeeded()` detects empty `$PREFIX`
2. Loads `libtermux-bootstrap.so` via `System.loadLibrary()`
3. Calls native `getZip()` to get bytes
4. Extracts entry-by-entry with `ZipInputStream` to staging directory
5. Processes `SYMLINKS.txt` to create symlinks
6. Atomically renames staging -> final `$PREFIX`
7. Applies `0700` permissions to `bin/`, `libexec/`, `lib/apt/`

### SYMLINKS.txt Format
```
target←relative/path
```
Separator: `←` character. Example: `coreutils←bin/ls` creates symlink `bin/ls -> coreutils`

---

## Essential Bootstrap Packages (~37MB installed, ~25MB compressed)

| Package | Size | Purpose |
|---|---|---|
| `bash` | 4.7MB | Primary shell |
| `coreutils` | 2.8MB | ls, cp, mv, chmod, etc. |
| `apt` | 4MB | Package manager |
| `dpkg` | 1.4MB | apt backend |
| `curl` | 0.4MB | HTTP downloads |
| `findutils` | 0.9MB | find, xargs |
| `grep` | 0.5MB | Text search |
| `sed` | 0.4MB | Stream editor |
| `tar` | 1.3MB | Archiving |
| `gzip` | 0.3MB | Compression |
| `less` | 0.4MB | Pager |
| `dash` | 0.2MB | POSIX shell (lightweight) |
| `procps` | 0.7MB | ps, top, kill |
| `util-linux` | 4.2MB | mount, lsblk, etc. |
| `gawk` | 3.3MB | Text processing |
| `diffutils` | 0.7MB | diff, cmp |
| `bzip2`, `xz-utils` | ~0.5MB | Additional compression |
| `termux-exec` | 2.1MB | W^X bypass via linker |
| `termux-core` | 2MB | Core utilities |
| `termux-tools` | 0.6MB | termux-info, etc. |
| `termux-keyring` | small | GPG repo keys |
| `termux-licenses` | small | Package licenses |
| `libc++`, `libcurl`, etc. | ~5MB | Shared libraries |

Additional in `generate-bootstraps.sh`: `ed`, `debianutils`, `dos2unix`, `inetutils`, `lsof`, `nano`, `net-tools`, `patch`, `unzip`, `command-not-found`.

---

## W^X Restriction (Android 10+)

**Critical for NovaTerm.** Android 10+ blocks `execve()` on files in app data directories.

### The Problem
Files in `/data/data/com.novaterm/files/usr/bin/` cannot be executed directly:
```c
execve("/data/data/com.novaterm/files/usr/bin/bash", ...) // FAILS with EACCES
```

### The Solution: termux-exec
Uses the system linker as intermediary:
```c
// Instead of direct execve, termux-exec translates to:
execve("/system/bin/linker64",
    ["/system/bin/linker64", "/data/data/com.novaterm/files/usr/bin/bash"],
    env)
```

`termux-exec` intercepts exec calls via `LD_PRELOAD` and redirects them through the linker.

**This package is CRITICAL and must be in the bootstrap.**

---

## Package Repository

### Termux Repos
- Main: `https://packages-cf.termux.dev/apt/termux-main`
- Root: `https://packages-cf.termux.dev/apt/termux-root`
- X11: `https://packages-cf.termux.dev/apt/termux-x11`

### Repo Structure (standard Debian)
```
dists/stable/main/binary-aarch64/Packages
dists/stable/main/binary-aarch64/Packages.xz
pool/main/<letter>/<package>/<package>_<version>_aarch64.deb
```

### The Package Name Problem
Termux packages are compiled with prefix `/data/data/com.termux/files/usr`.
NovaTerm uses package name `com.novaterm.app`, so prefix would be `/data/data/com.novaterm.app/files/usr`.

**Options:**
1. **Use Termux repos as-is**: Paths are hardcoded but most binaries use `$PREFIX` env var. Some have hardcoded paths in config files. This mostly works with `termux-exec` handling path translation.
2. **Build own packages**: Use `termux-packages` build system with `TERMUX_APP_PACKAGE=com.novaterm.app`. High effort but correct paths.
3. **Hybrid**: Use Termux repos for Phase 1, build custom for Phase 2.

**Recommendation:** Option 1 for Phase 1. Set env vars correctly and rely on termux-exec for path translation.

---

## Filesystem Layout

```
/data/data/com.novaterm.app/
  files/
    usr/                    ← $PREFIX
      bin/                  ← Executables
      lib/                  ← Shared libraries
      etc/                  ← Configuration
      tmp/                  ← $TMPDIR
      var/                  ← State (dpkg db, apt cache)
      share/                ← Data, man pages, licenses
    home/                   ← $HOME
      .bashrc
      .profile
```

### Symlinks
- Work correctly in `/data/data/` (ext4/f2fs)
- Do NOT work on `/storage/emulated/0/` (FUSE/sdcardfs)
- Termux creates symlinks extensively in `bin/` (e.g., `ls -> coreutils`)

### SELinux
- Context: `u:object_r:app_data_file:s0:cXXX,cYYY,cZZZ`
- Files accessible only by own app (uid-based sandbox)
- No root needed, no special permissions

### Permissions
- Executables: `0700` (rwx owner only)
- Other files: `0600` by default
- Owner: app UID (e.g., `u0_a382`)

---

## Environment Variables

Variables NovaTerm must export before launching shell:

```bash
# Fundamental directories
HOME=/data/data/com.novaterm.app/files/home
PREFIX=/data/data/com.novaterm.app/files/usr
TMPDIR=/data/data/com.novaterm.app/files/usr/tmp

# Execution
PATH=/data/data/com.novaterm.app/files/usr/bin
LD_PRELOAD=/data/data/com.novaterm.app/files/usr/lib/libtermux-exec-ld-preload.so
SHELL=/data/data/com.novaterm.app/files/usr/bin/bash

# Terminal
TERM=xterm-256color
COLORTERM=truecolor
LANG=en_US.UTF-8

# Android
ANDROID_DATA=/data
ANDROID_ROOT=/system

# App metadata (Termux v0.119+ format)
TERMUX_VERSION=0.1.0
TERMUX_APP__PACKAGE_NAME=com.novaterm.app
TERMUX_APP__DATA_DIR=/data/data/com.novaterm.app
```

**Important:** Do NOT set `LD_LIBRARY_PATH` on Android >= 7 (binaries use `DT_RUNPATH`).

---

## Compression Strategy

| Format | Ratio | Decompression Speed | Memory | Recommendation |
|---|---|---|---|---|
| **zstd** | Good | **Very fast** | Low | **Best for mobile** |
| xz/lzma | Excellent | Slow | High (1GB at max) | Avoid on mobile |
| gzip | Acceptable | Fast | Low | Fallback |
| zip (deflate) | Acceptable | Fast | Low | What Termux uses now |

**Recommendation:** zstd level 19 for bootstrap download. ~6% larger than xz but 5-10x faster decompression, which is critical on mobile.

---

## Bootstrap Strategy for NovaTerm

### Option A: Embedded in APK (like Termux)
```
Pros: Works offline, instant installation
Cons: Large APK (+25MB per arch), updates require new APK
```

### Option B: Download on First Use
```
Pros: Small APK, bootstrap updatable without Play Store
Cons: Requires internet on first use, waiting UX
```

### Option C: Hybrid (Recommended)
```
1. APK includes micro-bootstrap (~5-10MB): sh + busybox static + downloader
2. First use downloads full bootstrap (~20MB compressed zstd)
3. Additional packages via apt afterward
```

### CDN/Hosting
- GitHub Releases (free, global CDN)
- Alternative: CloudFlare R2 or bunny.net
- SHA-256 checksum verification before extraction

---

## Implementation Plan

### Phase 1: Minimal Viable Bootstrap

1. **Download bootstrap archive** from GitHub Releases on first launch
   - Show progress UI with download speed and ETA
   - Support resume on interrupted downloads
   - Verify SHA-256 checksum

2. **Extract to staging directory**
   - Use `ZipInputStream` (or zstd decoder + tar)
   - Process `SYMLINKS.txt` for symlink creation
   - Set permissions (`0700` for executables)

3. **Atomic move** staging -> final `$PREFIX`

4. **Configure environment** (env vars above)

5. **Launch shell** with proper environment

### Phase 1.5: Package Management

1. **Configure apt** to use Termux repositories
   - Write `sources.list` pointing to Termux main repo
   - Install Termux keyring for GPG verification

2. **User can install packages**: `apt install git python nodejs vim`

3. **PRoot-distro integration** for full Linux distros
   - `proot-distro install debian` → full Debian in ~500MB
   - ~95% native performance (ptrace-based syscall translation)
   - No root, no VM, no special permissions

---

## Legal Considerations

### Package Licenses
- **termux-app**: GPLv3 (the app itself)
- **terminal-emulator, terminal-view**: Apache 2.0 (what NovaTerm uses)
- **Individual packages**: Each has its own license (bash=GPL, coreutils=GPL, curl=MIT, etc.)

### Redistributing GPL Binaries
- Must offer corresponding source code
- Termux facilitates via `termux-licenses` package
- **Safest approach**: Include link to source code in app + install `termux-licenses`

### Using Termux Repos
- Downloading packages at runtime (like apt does) is NOT redistribution
- The bootstrap archive IS redistribution if we bundle it
- Building from termux-packages source ourselves avoids redistribution concerns

---

## Alternative: PRoot-distro (Phase 1.5)

For users who want a full Linux environment:

```bash
# Install proot-distro
apt install proot-distro

# Install Debian
proot-distro install debian

# Login to Debian
proot-distro login debian

# Now you're in a full Debian environment
apt install gcc python3 nodejs
```

**Performance:** ~95% native (ptrace overhead is minimal for most workloads)
**Storage:** ~500MB-2GB depending on distro + packages
**Advantage over Google's Android 16 Terminal:** Works on ALL phones, not just Pixel/Tensor

---

## Sources

- [Termux Bootstrap Releases](https://github.com/termux/termux-packages/releases)
- [TermuxInstaller.java](https://github.com/termux/termux-app/blob/master/app/src/main/java/com/termux/app/TermuxInstaller.java)
- [generate-bootstraps.sh](https://github.com/termux/termux-packages/blob/master/scripts/generate-bootstraps.sh)
- [Termux Execution Environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment)
- [termux-exec System Linker](https://deepwiki.com/termux/termux-exec-package)
- [Building Custom Termux](https://hongchai.medium.com/building-your-own-termux-with-a-custom-package-name-4b2de0c09fac)
- [termux-apt-repo](https://pypi.org/project/termux-apt-repo/)
- [Zstandard Compression](https://facebook.github.io/zstd/)
