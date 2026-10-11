import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import {
  canImportGuest,
  KEY,
  readGuest,
  removeImportedGuest,
  resetGuest,
  writeGuest,
} from "./guestRecordStorage.ts";

beforeEach(() => {
  const values = new Map();
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: (key) => values.delete(key),
    },
  });
});
const record = (id, ended = false) => ({
  id,
  start: Date.now() - 86400000,
  ...(ended ? { end: Date.now() - 3600000 } : {}),
  daily: 15,
  price: 4500,
  packSize: 20,
  clientId: crypto.randomUUID(),
  growth: 0,
  attendance: [],
  rewards: [],
});

test("imported guest stays empty on reload even with legacy records present", () => {
  const guest = record(8);
  writeGuest([guest]);
  localStorage.setItem("soom-ui-v2", JSON.stringify({ records: [guest] }));
  localStorage.setItem("soom-guest-dashboard-v1", JSON.stringify({ points: 50 }));
  removeImportedGuest(guest.clientId);
  assert.equal(localStorage.getItem(KEY), "[]");
  assert.deepEqual(readGuest(), []);
  assert.deepEqual(readGuest(), []);
  assert.equal(localStorage.getItem("soom-guest-dashboard-v1"), null);
});

test("import cleanup preserves a different guest record", () => {
  const newer = record(9);
  writeGuest([newer]);
  removeImportedGuest(crypto.randomUUID());
  assert.deepEqual(readGuest(), [newer]);
});

test("existing guest history is removed while the current record survives reloads", () => {
  const active = record(5);
  localStorage.setItem(KEY, JSON.stringify([record(4, true), active]));
  assert.deepEqual(readGuest(), [active]);
  assert.deepEqual(JSON.parse(localStorage.getItem(KEY)), [active]);
  assert.deepEqual(readGuest(), [active]);
});
test("legacy guest migration retains only the current non-demo record", () => {
  localStorage.setItem(
    "soom-ui-v2",
    JSON.stringify({ records: [record(1), record(3, true), record(4)] }),
  );
  assert.deepEqual(
    readGuest().map((r) => r.id),
    [4],
  );
});
test("ended-only guest history does not become an import candidate", () => {
  writeGuest([record(3, true)]);
  assert.deepEqual(readGuest(), []);
});
test("reset starts a fresh single record, keeps smoking settings and clears guest rewards", () => {
  const old = { ...record(5), attendance: ["2026-10-10"], rewards: [20] };
  writeGuest([old]);
  localStorage.setItem("soom-guest-dashboard-v1", JSON.stringify({ points: 50, wishes: [1] }));
  const before = Date.now();
  const next = resetGuest(old);
  assert.deepEqual(readGuest(), JSON.parse(JSON.stringify([next])));
  assert.notEqual(next.id, old.id);
  assert.notEqual(next.clientId, old.clientId);
  assert.ok(next.start >= before);
  assert.equal(next.end, undefined);
  assert.deepEqual([next.daily, next.price, next.packSize], [15, 4500, 20]);
  assert.deepEqual(next.attendance, []);
  assert.deepEqual(next.rewards, []);
  assert.equal(localStorage.getItem("soom-guest-dashboard-v1"), null);
});
test("import stays hidden for guests, unresolved accounts and accounts with an active record", () => {
  assert.equal(canImportGuest(false, "empty", []), false);
  for (const status of ["initial", "loading", "error"])
    assert.equal(canImportGuest(true, status, []), false);
  assert.equal(canImportGuest(true, "ready", [record(7)]), false);
});
test("loaded accounts without a current record can be offered the guest record", () => {
  assert.equal(canImportGuest(true, "empty", []), true);
  assert.equal(canImportGuest(true, "ready", [record(7, true)]), true);
});
