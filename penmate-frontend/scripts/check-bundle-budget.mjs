import { readFile, readdir, stat } from 'node:fs/promises'
import path from 'node:path'
import { gzipSync } from 'node:zlib'

const dist = path.resolve('dist')
const assets = path.join(dist, 'assets')
const html = await readFile(path.join(dist, 'index.html'), 'utf8')
const initialFiles = [...html.matchAll(/(?:src|href)="\/(assets\/[^"?]+\.js)"/g)].map((match) => match[1])
const initialSizes = await Promise.all(
  initialFiles.map(async (file) => gzipSync(await readFile(path.join(dist, file))).byteLength),
)
const initialGzipBytes = initialSizes.reduce((total, bytes) => total + bytes, 0)

const files = await readdir(assets)
const jsFiles = files.filter((file) => file.endsWith('.js'))
const imageFiles = files.filter((file) => /\.(?:avif|webp|png|jpe?g)$/i.test(file))
const jsSizes = await Promise.all(
  jsFiles.map(async (file) => ({ file, gzip: gzipSync(await readFile(path.join(assets, file))).byteLength })),
)
const imageSizes = await Promise.all(
  imageFiles.map(async (file) => ({ file, bytes: (await stat(path.join(assets, file))).size })),
)

const limits = {
  initialGzip: 250 * 1024,
  routeGzip: 150 * 1024,
  singleImage: 300 * 1024,
  allImages: 2 * 1024 * 1024,
}

const failures = []
if (initialGzipBytes > limits.initialGzip) failures.push(`initial JS ${Math.round(initialGzipBytes / 1024)} KB gzip`)
for (const item of jsSizes.filter((item) => item.gzip > limits.routeGzip))
  failures.push(`${item.file} ${Math.round(item.gzip / 1024)} KB gzip`)
for (const item of imageSizes.filter((item) => item.bytes > limits.singleImage))
  failures.push(`${item.file} ${Math.round(item.bytes / 1024)} KB`)
const totalImageBytes = imageSizes.reduce((total, item) => total + item.bytes, 0)
if (totalImageBytes > limits.allImages) failures.push(`all images ${Math.round(totalImageBytes / 1024)} KB`)

console.log(
  `Initial JS: ${Math.round(initialGzipBytes / 1024)} KB gzip; images: ${Math.round(totalImageBytes / 1024)} KB`,
)
if (failures.length) {
  throw new Error(`Bundle budget exceeded: ${failures.join(', ')}`)
}
