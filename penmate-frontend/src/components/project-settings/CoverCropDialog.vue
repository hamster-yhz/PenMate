<template>
  <AModal
    :open="open"
    :width="620"
    :footer="null"
    title="裁切作品封面"
    :mask-closable="false"
    @cancel="emit('close')"
  >
    <div class="crop-dialog-body">
      <div class="crop-stage" :class="{ loading: imageLoading }">
        <canvas
          ref="canvasRef"
          width="320"
          height="480"
          aria-label="拖动图片调整封面裁切范围"
          @pointerdown="startDrag"
          @pointermove="drag"
          @pointerup="endDrag"
          @pointercancel="endDrag"
        ></canvas>
        <span v-if="imageLoading">正在读取图片…</span>
      </div>

      <label class="zoom-control">
        <span><ZoomOutOutlined />缩放</span>
        <input v-model.number="zoom" type="range" min="1" max="3" step="0.01" @input="onZoom" />
        <ZoomInOutlined />
      </label>
      <p>拖动图片选择保留区域，输出比例固定为 2:3。</p>

      <div class="crop-actions">
        <button type="button" @click="emit('close')">取消</button>
        <button class="primary" type="button" :disabled="imageLoading || !image" @click="confirm">
          <CheckOutlined />使用这个裁切
        </button>
      </div>
    </div>
  </AModal>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { CheckOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons-vue'
import { Modal as AModal } from 'ant-design-vue'
import type { NovelCoverCrop } from '@/entities/novel/model'

const STAGE_WIDTH = 320
const STAGE_HEIGHT = 480

const props = defineProps<{
  open: boolean
  sourceUrl: string
  initialCrop?: NovelCoverCrop | null
}>()

const emit = defineEmits<{
  close: []
  confirm: [crop: NovelCoverCrop]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const imageLoading = ref(false)
const zoom = ref(1)
const image = ref<HTMLImageElement | null>(null)
let baseScale = 1
let offsetX = 0
let offsetY = 0
let dragPointerId: number | null = null
let dragX = 0
let dragY = 0

const dimensions = () => {
  if (!image.value) return { width: 0, height: 0 }
  return {
    width: image.value.naturalWidth * baseScale * zoom.value,
    height: image.value.naturalHeight * baseScale * zoom.value,
  }
}

const clampOffsets = () => {
  const { width, height } = dimensions()
  const maxX = Math.max(0, (width - STAGE_WIDTH) / 2)
  const maxY = Math.max(0, (height - STAGE_HEIGHT) / 2)
  offsetX = Math.max(-maxX, Math.min(maxX, offsetX))
  offsetY = Math.max(-maxY, Math.min(maxY, offsetY))
}

const render = () => {
  const canvas = canvasRef.value
  const source = image.value
  if (!canvas || !source) return
  const context = canvas.getContext('2d')
  if (!context) return
  const { width, height } = dimensions()
  const left = (STAGE_WIDTH - width) / 2 + offsetX
  const top = (STAGE_HEIGHT - height) / 2 + offsetY
  context.clearRect(0, 0, STAGE_WIDTH, STAGE_HEIGHT)
  context.fillStyle = '#161a18'
  context.fillRect(0, 0, STAGE_WIDTH, STAGE_HEIGHT)
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(source, left, top, width, height)
}

const restoreCrop = (crop?: NovelCoverCrop | null) => {
  const source = image.value
  if (!source || !crop || crop.width <= 0 || crop.height <= 0) return
  const wantedWidth = STAGE_WIDTH / crop.width
  zoom.value = Math.max(1, Math.min(3, wantedWidth / (source.naturalWidth * baseScale)))
  const { width, height } = dimensions()
  offsetX = (width - STAGE_WIDTH) / 2 - crop.x * width
  offsetY = (height - STAGE_HEIGHT) / 2 - crop.y * height
  clampOffsets()
}

const loadImage = async () => {
  if (!props.open || !props.sourceUrl) return
  imageLoading.value = true
  image.value = null
  await nextTick()
  const nextImage = new Image()
  nextImage.decoding = 'async'
  nextImage.onload = () => {
    image.value = nextImage
    baseScale = Math.max(STAGE_WIDTH / nextImage.naturalWidth, STAGE_HEIGHT / nextImage.naturalHeight)
    zoom.value = 1
    offsetX = 0
    offsetY = 0
    restoreCrop(props.initialCrop)
    imageLoading.value = false
    render()
  }
  nextImage.onerror = () => {
    imageLoading.value = false
  }
  nextImage.src = props.sourceUrl
}

const pointerPosition = (event: PointerEvent) => {
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  return {
    x: (event.clientX - rect.left) * (STAGE_WIDTH / rect.width),
    y: (event.clientY - rect.top) * (STAGE_HEIGHT / rect.height),
  }
}

const startDrag = (event: PointerEvent) => {
  if (!image.value) return
  dragPointerId = event.pointerId
  const position = pointerPosition(event)
  dragX = position.x
  dragY = position.y
  canvasRef.value?.setPointerCapture(event.pointerId)
}

const drag = (event: PointerEvent) => {
  if (dragPointerId !== event.pointerId) return
  const position = pointerPosition(event)
  offsetX += position.x - dragX
  offsetY += position.y - dragY
  dragX = position.x
  dragY = position.y
  clampOffsets()
  render()
}

const endDrag = (event: PointerEvent) => {
  if (dragPointerId !== event.pointerId) return
  dragPointerId = null
  canvasRef.value?.releasePointerCapture(event.pointerId)
}

const onZoom = () => {
  clampOffsets()
  render()
}

const confirm = () => {
  const { width, height } = dimensions()
  emit('confirm', {
    x: Math.max(0, ((width - STAGE_WIDTH) / 2 - offsetX) / width),
    y: Math.max(0, ((height - STAGE_HEIGHT) / 2 - offsetY) / height),
    width: STAGE_WIDTH / width,
    height: STAGE_HEIGHT / height,
  })
}

watch(() => [props.open, props.sourceUrl] as const, loadImage, { immediate: true })
</script>

<style scoped>
.crop-dialog-body { display: grid; justify-items: center; gap: 18px; padding-top: 8px; }
.crop-stage { position: relative; display: grid; width: min(320px, 78vw, 39vh); aspect-ratio: 2 / 3; overflow: hidden; place-items: center; background: #161a18; border: 1px solid #3f4743; border-radius: 6px; }
.crop-stage canvas { display: block; width: 100%; height: 100%; cursor: grab; touch-action: none; }
.crop-stage canvas:active { cursor: grabbing; }
.crop-stage.loading canvas { opacity: .35; }
.crop-stage > span { position: absolute; color: #fff; font-size: 13px; }
.zoom-control { display: grid; grid-template-columns: auto minmax(160px, 280px) auto; width: min(100%, 430px); align-items: center; gap: 10px; color: var(--text-secondary); }
.zoom-control > span { display: inline-flex; align-items: center; gap: 6px; }
.zoom-control input { width: 100%; accent-color: var(--accent); }
.crop-dialog-body > p { margin: -8px 0 0; color: var(--text-muted); font-size: 12px; }
.crop-actions { display: flex; width: 100%; justify-content: flex-end; gap: 8px; padding-top: 4px; }
.crop-actions button { display: inline-flex; min-height: 36px; align-items: center; gap: 7px; padding: 0 13px; color: var(--text-secondary); background: var(--bg-surface); border: 1px solid var(--border-strong); border-radius: var(--radius-md); cursor: pointer; }
.crop-actions button.primary { color: var(--text-inverse); background: var(--accent); border-color: var(--accent); font-weight: 650; }
.crop-actions button:disabled { cursor: not-allowed; opacity: .55; }
</style>
