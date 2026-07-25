export type StoryBibleFieldControl =
  | 'string'
  | 'multiline'
  | 'string-list'
  | 'integer'
  | 'number'
  | 'boolean'
  | 'enum'

export interface StoryBibleSchemaSection {
  key: string
  title: string
  description?: string
}

export interface StoryBibleSchemaField {
  key: string
  title: string
  type: string
  control: StoryBibleFieldControl
  section: string
  order: number
  description?: string
  placeholder?: string
  enumValues: string[]
  enumLabels: Record<string, string>
  minimum?: number
  maximum?: number
}

export interface StoryBibleTypeSchema {
  title: string
  description: string
  color: string
  titlePlaceholder: string
  summaryPlaceholder: string
  sections: StoryBibleSchemaSection[]
  fields: StoryBibleSchemaField[]
}

type RawProperty = {
  title?: string
  type?: string
  description?: string
  enum?: string[]
  minimum?: number
  maximum?: number
  'x-penmate-control'?: StoryBibleFieldControl
  'x-penmate-section'?: string
  'x-penmate-order'?: number
  'x-penmate-placeholder'?: string
  'x-penmate-enum-labels'?: Record<string, string>
}

type RawSchema = {
  title?: string
  description?: string
  properties?: Record<string, RawProperty>
  'x-penmate-description'?: string
  'x-penmate-color'?: string
  'x-penmate-title-placeholder'?: string
  'x-penmate-summary-placeholder'?: string
  'x-penmate-sections'?: StoryBibleSchemaSection[]
}

const controlFrom = (property: RawProperty): StoryBibleFieldControl => {
  if (property['x-penmate-control']) return property['x-penmate-control']
  if (Array.isArray(property.enum)) return 'enum'
  if (property.type === 'array') return 'string-list'
  if (property.type === 'integer') return 'integer'
  if (property.type === 'number') return 'number'
  if (property.type === 'boolean') return 'boolean'
  return 'string'
}

export const parseStoryBibleTypeSchema = (schemaJson?: string | null): StoryBibleTypeSchema => {
  try {
    const schema = JSON.parse(schemaJson || '{}') as RawSchema
    const fields = Object.entries(schema.properties || {})
      .map(([key, property], index) => ({
        key,
        title: property.title || key,
        type: property.type || 'string',
        control: controlFrom(property),
        section: property['x-penmate-section'] || 'details',
        order: Number(property['x-penmate-order'] ?? (index + 1) * 10),
        description: property.description,
        placeholder: property['x-penmate-placeholder'],
        enumValues: Array.isArray(property.enum) ? property.enum : [],
        enumLabels: property['x-penmate-enum-labels'] || {},
        minimum: property.minimum,
        maximum: property.maximum,
      }))
      .sort((left, right) => left.order - right.order)
    const declaredSections = Array.isArray(schema['x-penmate-sections']) ? schema['x-penmate-sections'] : []
    const sectionKeys = new Set(declaredSections.map((section) => section.key))
    const inferredSections = fields
      .filter((field) => !sectionKeys.has(field.section))
      .map((field) => field.section)
      .filter((key, index, all) => all.indexOf(key) === index)
      .map((key) => ({ key, title: key === 'details' ? '专属字段' : key }))
    return {
      title: schema.title || '',
      description: schema['x-penmate-description'] || schema.description || '',
      color: schema['x-penmate-color'] || '#6f8fa8',
      titlePlaceholder: schema['x-penmate-title-placeholder'] || '设定名称',
      summaryPlaceholder: schema['x-penmate-summary-placeholder'] || '用一两句话概括这项设定',
      sections: [...declaredSections, ...inferredSections],
      fields,
    }
  } catch {
    return {
      title: '',
      description: '',
      color: '#6f8fa8',
      titlePlaceholder: '设定名称',
      summaryPlaceholder: '用一两句话概括这项设定',
      sections: [],
      fields: [],
    }
  }
}

export const storyBibleSchemaFieldCount = (schemaJson?: string | null) =>
  parseStoryBibleTypeSchema(schemaJson).fields.length
