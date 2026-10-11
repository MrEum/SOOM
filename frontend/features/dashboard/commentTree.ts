type CommentLink = { id: number; parent?: number; replyTo?: number };

export function visibleComments<T extends CommentLink>(comments: T[], expanded: Set<number>) {
  const children = new Map<number | undefined, T[]>();
  for (const comment of comments) {
    const target = comment.replyTo ?? comment.parent;
    const siblings = children.get(target) ?? [];
    siblings.push(comment);
    children.set(target, siblings);
  }
  const stack = (children.get(undefined) ?? [])
    .slice()
    .reverse()
    .map((comment) => ({ comment, depth: 0 }));
  const result: { comment: T; depth: number; replyCount: number; expanded: boolean }[] = [];
  const visited = new Set<number>();
  while (stack.length) {
    const item = stack.pop()!;
    if (visited.has(item.comment.id)) continue;
    visited.add(item.comment.id);
    const replies = children.get(item.comment.id) ?? [];
    const open = expanded.has(item.comment.id);
    result.push({ ...item, replyCount: replies.length, expanded: open });
    const visible = open ? replies : [];
    for (let i = visible.length - 1; i >= 0; i--)
      stack.push({ comment: visible[i], depth: item.depth + 1 });
  }
  return result;
}
