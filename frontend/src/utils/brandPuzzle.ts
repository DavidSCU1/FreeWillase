export type BrandPuzzlePieceTemplate = {
  id: string
  targetX: number
  targetY: number
  width: number
  height: number
  clipPath: string
  tint: string
  glow: string
}

export const BRAND_PUZZLE_BOARD_UNITS = 64

export const brandPuzzleTemplates: BrandPuzzlePieceTemplate[] = [
  {
    id: 'top-left',
    targetX: 0,
    targetY: 0,
    width: 21,
    height: 21,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(56,189,248,0.04)',
    glow: 'rgba(56,189,248,0.1)',
  },
  {
    id: 'top-center',
    targetX: 21,
    targetY: 0,
    width: 22,
    height: 21,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(94,234,212,0.04)',
    glow: 'rgba(94,234,212,0.1)',
  },
  {
    id: 'top-right',
    targetX: 43,
    targetY: 0,
    width: 21,
    height: 21,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(56,189,248,0.04)',
    glow: 'rgba(56,189,248,0.1)',
  },
  {
    id: 'middle-left',
    targetX: 0,
    targetY: 21,
    width: 21,
    height: 22,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(139,92,246,0.04)',
    glow: 'rgba(139,92,246,0.1)',
  },
  {
    id: 'middle-center',
    targetX: 21,
    targetY: 21,
    width: 22,
    height: 22,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(56,189,248,0.03)',
    glow: 'rgba(56,189,248,0.08)',
  },
  {
    id: 'middle-right',
    targetX: 43,
    targetY: 21,
    width: 21,
    height: 22,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(94,234,212,0.04)',
    glow: 'rgba(94,234,212,0.1)',
  },
  {
    id: 'bottom-left',
    targetX: 0,
    targetY: 43,
    width: 21,
    height: 21,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(56,189,248,0.04)',
    glow: 'rgba(56,189,248,0.1)',
  },
  {
    id: 'bottom-center',
    targetX: 21,
    targetY: 43,
    width: 22,
    height: 21,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(94,234,212,0.04)',
    glow: 'rgba(94,234,212,0.1)',
  },
  {
    id: 'bottom-right',
    targetX: 43,
    targetY: 43,
    width: 21,
    height: 21,
    clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)',
    tint: 'rgba(94,234,212,0.04)',
    glow: 'rgba(94,234,212,0.1)',
  },
]
