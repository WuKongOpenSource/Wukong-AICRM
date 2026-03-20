<template>
  <i
    class="wk wk-icon"
    :class="`wk-icon-${wkIconClassMap[name]}`"
    :style="iconStyle"
    :aria-hidden="label ? undefined : 'true'"
    :aria-label="label"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CSSProperties } from 'vue'
import { wkIconClassMap } from './wkIcon'
import type { WkIconName } from './wkIcon'

const props = withDefaults(defineProps<{
  name: WkIconName
  size?: number | string
  color?: string
  label?: string
}>(), {
  color: 'currentColor',
  label: undefined,
})

const iconStyle = computed<CSSProperties>(() => {
  const style: CSSProperties = {
    color: props.color,
  }

  if (props.size !== undefined) {
    style.fontSize = typeof props.size === 'number' ? `${props.size}px` : props.size
  }

  return style
})
</script>
