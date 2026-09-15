// 指数退避：每次失败等待时间翻倍并封顶 max；成功后 reset 回到 min。
// 抽成纯函数便于单测（npm run test:backoff）。
export const createBackoff = ({ min, max }) => {
  let current = min
  return {
    /** 返回本次应等待的毫秒数，并把下次的等待时间翻倍（不超过 max） */
    next() {
      const wait = current
      current = Math.min(current * 2, max)
      return wait
    },
    reset() {
      current = min
    }
  }
}
