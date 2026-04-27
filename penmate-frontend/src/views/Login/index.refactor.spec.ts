import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readLoginSource = () => readFileSync(resolve(currentDir, 'index.vue'), 'utf-8')

describe('Login index refactor', () => {
  it('reduces the page to auth shell components and composable wiring', () => {
    const source = readLoginSource()

    expect(source).toContain("import AuthCardShell from '@/components/auth/AuthCardShell.vue'")
    expect(source).toContain("import AuthModeTabs from '@/components/auth/AuthModeTabs.vue'")
    expect(source).toContain("import LoginForm")
    expect(source).toContain("import RegisterForm from '@/components/auth/RegisterForm.vue'")
    expect(source).toContain("useLoginSubmit()")

    expect(source).toContain('<AuthCardShell>')
    expect(source).toContain('<AuthModeTabs v-model="mode" />')
    expect(source).toContain('<LoginForm')
    expect(source).toContain('<RegisterForm')

    expect(source).not.toContain('const handleSubmit = async () =>')
    expect(source).not.toContain('authApi.login({')
    expect(source).not.toContain('authApi.me()')
    expect(source).not.toContain('setSession({')
    expect(source).not.toContain('<form class="login-form" @submit.prevent="handleSubmit" v-if="mode === \'login\'">')
    expect(source).not.toContain('<form class="login-form" @submit.prevent="handleSubmit" v-else>')
  })
})
