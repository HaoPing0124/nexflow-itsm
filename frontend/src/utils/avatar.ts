/**
 * 头像：按用户名生成稳定的真实人像照片地址。
 *
 * 用 randomuser.me 的官方人像集（非真人身份声明，纯演示用途）：
 * 面相正式、清晰、光照统一，且同一 seed 永远得到同一张照片，
 * 因此列表里同一个人不会每次刷新换脸。
 */
export function avatarUrl(seed: string | null | undefined, _size = 128): string {
  const raw = ((seed ?? 'user').trim() || 'user').toLowerCase()
  // 简易稳定散列：把用户名映射到 [0, 99] 的确定下标 + 男女分组
  let hash = 0
  for (let i = 0; i < raw.length; i++) {
    hash = (hash * 31 + raw.charCodeAt(i)) >>> 0
  }
  const group = hash % 2 === 0 ? 'men' : 'women'
  const index = hash % 100
  return `https://randomuser.me/api/portraits/${group}/${index}.jpg`
}