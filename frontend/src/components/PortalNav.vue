<template>
  <nav class="portal-nav" :class="{ 'is-open': open }" aria-label="门户导航">
    <RouterLink to="/" class="portal-nav-link" exact-active-class="is-active" @click="close">
      首页
    </RouterLink>
    <RouterLink to="/assets-public" class="portal-nav-link" active-class="is-active" @click="close">
      资产库
    </RouterLink>
    <RouterLink to="/statistics-public" class="portal-nav-link" active-class="is-active" @click="close">
      数据台
    </RouterLink>
    <RouterLink to="/about" class="portal-nav-link" active-class="is-active" @click="close">
      关于平台
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const close = () => emit('close')
</script>
