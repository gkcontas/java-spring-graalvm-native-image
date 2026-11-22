#!/usr/bin/env python3
"""Boot, memory, size and throughput of the same application in four modes,
measured in one session.

Usage: python3 scripts/measure.py
Needs: PostgreSQL from docker compose up, build/libs/<jar> (./gradlew bootJar),
build/native/native-image-catalog (copied out of the native image) and the
Gradle daemon stopped. The application is pinned to cores 0-3 and the load
generator to the remaining cores, so they do not compete for CPU.
"""
import json
import os
import re
import shutil
import statistics
import subprocess
import sys
import time
import urllib.request

JAVA = os.environ.get("JAVA21", "/home/gustavo/.jdks/corretto-21.0.5/bin/java")
JAR = "build/libs/native-image-catalog-1.0.0.jar"
NATIVE = "build/native/native-image-catalog"
CDS_DIR = "build/cds"
APP = "http://localhost:8080"
APP_CPUS = "0-3"
LOAD_CPUS = "4-11"
HEAP = "-Xmx512m"
BOOT_RUNS = int(os.environ.get("BOOT_RUNS", "7"))
PRODUCTS = 100_000
THREADS = 16
COLD_REQUESTS = 2_000
WARMUP = "45s"
STEADY = "30s"


def mode_cmd(mode, extra=()):
    if mode == "native":
        return [NATIVE, HEAP, *extra]
    if mode == "jvm":
        return [JAVA, HEAP, "-jar", JAR, *extra]
    if mode == "jvm-aot":
        return [JAVA, HEAP, "-Dspring.aot.enabled=true", "-jar", JAR, *extra]
    if mode == "jvm-aot-cds":
        return [JAVA, HEAP, "-Dspring.aot.enabled=true", f"-XX:SharedArchiveFile={CDS_DIR}/app.jsa",
                "-jar", f"{CDS_DIR}/app/native-image-catalog-1.0.0.jar", *extra]
    raise ValueError(mode)


def http(method, path, timeout=5):
    req = urllib.request.Request(APP + path, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, json.loads(r.read() or b"null")


def rss_mb(pid):
    with open(f"/proc/{pid}/status") as f:
        for line in f:
            if line.startswith("VmRSS:"):
                return int(line.split()[1]) / 1024


def start(mode, extra=()):
    log = open(f"build/{mode}.log", "w")
    t0 = time.perf_counter()
    proc = subprocess.Popen(["taskset", "-c", APP_CPUS, *mode_cmd(mode, extra)], stdout=log, stderr=subprocess.STDOUT)
    while True:
        if proc.poll() is not None:
            raise RuntimeError(f"{mode} exited with {proc.returncode}, see build/{mode}.log")
        try:
            if http("GET", "/actuator/health", timeout=1)[0] == 200:
                return proc, (time.perf_counter() - t0) * 1000
        except Exception:
            time.sleep(0.005)


def stop(proc):
    proc.terminate()
    proc.wait(timeout=30)


def started_in(mode):
    text = open(f"build/{mode}.log").read()
    m = re.search(r"Started \w+ in ([\d.]+) seconds", text)
    return float(m.group(1)) * 1000


def load(spec):
    out = subprocess.run(["taskset", "-c", LOAD_CPUS, "java", "scripts/LoadGen.java", APP, str(PRODUCTS),
                          str(THREADS), spec], capture_output=True, text=True, check=True)
    return json.loads(out.stdout.strip().splitlines()[-1])


def prepare_cds():
    shutil.rmtree(CDS_DIR, ignore_errors=True)
    subprocess.run([JAVA, "-Djarmode=tools", "-jar", JAR, "extract", "--destination", f"{CDS_DIR}/app"],
                   check=True, capture_output=True)
    # Training run: start, refresh the context, exit, and dump the classes loaded.
    subprocess.run([JAVA, "-Dspring.aot.enabled=true", f"-XX:ArchiveClassesAtExit={CDS_DIR}/app.jsa",
                    "-Dspring.context.exit=onRefresh", "-jar", f"{CDS_DIR}/app/native-image-catalog-1.0.0.jar"],
                   check=True, capture_output=True)


def section(title):
    print(f"\n== {title}", flush=True)


results = {}
modes = ["jvm", "jvm-aot", "jvm-aot-cds", "native"]

section("sizes")
prepare_cds()
results["sizes"] = {
    "jar_mb": round(os.path.getsize(JAR) / 1e6, 1),
    "native_mb": round(os.path.getsize(NATIVE) / 1e6, 1),
    "cds_archive_mb": round(os.path.getsize(f"{CDS_DIR}/app.jsa") / 1e6, 1),
}
print(results["sizes"])

section("seed")
proc, _ = start("jvm")
print(http("POST", f"/api/admin/seed?count={PRODUCTS}", timeout=120)[1])
stop(proc)

section(f"boot: {BOOT_RUNS} runs per mode, median")
results["boot"] = {}
for mode in modes:
    ready, started, idle = [], [], []
    for _ in range(BOOT_RUNS):
        proc, ms = start(mode)
        time.sleep(1)
        idle.append(rss_mb(proc.pid))
        stop(proc)
        ready.append(ms)
        started.append(started_in(mode))
    results["boot"][mode] = {
        "ready_ms": round(statistics.median(ready)),
        "spring_started_ms": round(statistics.median(started)),
        "idle_rss_mb": round(statistics.median(idle)),
    }
    print(mode, results["boot"][mode], flush=True)

section(f"load: {THREADS} threads, first {COLD_REQUESTS} requests, warm-up {WARMUP}, steady {STEADY}")
results["load"] = {}
for mode in modes:
    proc, _ = start(mode)
    cold = load(str(COLD_REQUESTS))
    load(WARMUP)
    steady = load(STEADY)
    results["load"][mode] = {"cold": cold, "steady": steady, "rss_after_load_mb": round(rss_mb(proc.pid))}
    stop(proc)
    print(mode, results["load"][mode], flush=True)

section("conditions frozen at build time: --app.audit.enabled=true")
results["frozen"] = {}
for mode in ["jvm", "jvm-aot", "native"]:
    proc, _ = start(mode, ["--app.audit.enabled=true", "--app.greeting=hola"])
    d = http("GET", "/api/diagnostics")[1]
    stop(proc)
    results["frozen"][mode] = {"audit": d["auditTrail"], "greeting": d["greeting"]}
    print(mode, results["frozen"][mode], flush=True)

section("closed world: probes with and without hints")
results["probes"] = {}
for mode in ["jvm", "native"]:
    proc, _ = start(mode)
    d = http("GET", "/api/diagnostics")[1]
    stop(proc)
    results["probes"][mode] = {"mode": d["mode"], "probes": d["probes"]}
    print(mode, json.dumps(results["probes"][mode], indent=1), flush=True)

with open("build/measurements.json", "w") as f:
    json.dump(results, f, indent=2)
print("\nsaved build/measurements.json")
