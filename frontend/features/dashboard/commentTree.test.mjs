import assert from "node:assert/strict";
import { test } from "node:test";
import { visibleComments } from "./commentTree.ts";
const comments = [
  { id: 1 },
  { id: 2, parent: 1, replyTo: 1 },
  { id: 3, parent: 1, replyTo: 1 },
  { id: 4, parent: 1, replyTo: 2 },
  { id: 5, parent: 1, replyTo: 4 },
  { id: 6 },
];
test("all replies including a single reply are collapsed initially", () => {
  assert.deepEqual(
    visibleComments(comments, new Set()).map((x) => x.comment.id),
    [1, 6],
  );
  assert.deepEqual(
    visibleComments([{ id: 1 }, { id: 2, parent: 1 }], new Set()).map((x) => x.comment.id),
    [1],
  );
});
test("expanding root leaves replies to replies collapsed", () => {
  assert.deepEqual(
    visibleComments(comments, new Set([1])).map((x) => x.comment.id),
    [1, 2, 3, 6],
  );
  assert.deepEqual(
    visibleComments(comments, new Set([1, 2])).map((x) => x.comment.id),
    [1, 2, 4, 3, 6],
  );
  assert.deepEqual(
    visibleComments(comments, new Set([1, 2, 4])).map((x) => x.comment.id),
    [1, 2, 4, 5, 3, 6],
  );
});
test("counts describe direct conversation replies and collapse hides their branch", () => {
  const rows = visibleComments(comments, new Set([1, 2]));
  assert.equal(rows[0].replyCount, 2);
  assert.equal(rows[1].replyCount, 1);
  assert.deepEqual(
    visibleComments(comments, new Set([2])).map((x) => x.comment.id),
    [1, 6],
  );
});
test("deleted parent can still expand preserved descendants", () => {
  assert.equal(
    visibleComments(
      [
        { id: 1, deleted: true },
        { id: 2, parent: 1, replyTo: 1 },
      ],
      new Set([1]),
    ).length,
    2,
  );
});
