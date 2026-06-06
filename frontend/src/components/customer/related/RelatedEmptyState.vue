<template>
  <div class="rounded-2xl border-2 border-dashed border-slate-200 bg-slate-50/70 py-4 text-center">
    <span class="material-symbols-outlined text-2xl leading-none text-slate-400">{{ icon }}</span>
    <p class="mt-2 text-xs font-medium text-slate-400">{{ text }}</p>
    <p v-if="hint" class="mt-1 text-xs font-medium text-slate-300">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  icon: string
  text: string
  hint?: string
}>()
</script>
