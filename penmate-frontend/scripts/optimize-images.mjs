import { readdir } from 'node:fs/promises'
import path from 'node:path'
import sharp from 'sharp'

const imageDirectory = path.resolve('src/assets/images')
const files = await readdir(imageDirectory)

for (const file of files.filter((name) => name.endsWith('.png'))) {
  const source = path.join(imageDirectory, file)
  const target = path.join(imageDirectory, file.replace(/\.png$/, '.webp'))
  await sharp(source).webp({ quality: 82, alphaQuality: 90, effort: 6 }).toFile(target)
}
