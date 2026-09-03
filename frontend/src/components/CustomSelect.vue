<template>
  <div class="custom-select" ref="selectRef">
    <div
      class="select-trigger"
      :class="{ 'is-open': isOpen, 'is-disabled': disabled }"
      @click="toggleDropdown"
    >
      <span class="select-value">{{ displayValue }}</span>
      <svg class="select-arrow" viewBox="0 0 12 12">
        <path fill="currentColor" d="M6 9L1 4h10z"/>
      </svg>
    </div>

    <Teleport to="body">
      <div
        v-if="isOpen"
        class="select-dropdown"
        :style="dropdownStyle"
        ref="dropdownRef"
      >
        <div
          v-for="option in options"
          :key="option.value"
          class="select-option"
          :class="{ 'is-selected': option.value === modelValue }"
          @click="selectOption(option)"
        >
          {{ option.label }}
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

interface SelectOption {
  label: string
  value: string
}

const props = defineProps<{
  modelValue: string
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const selectRef = ref<HTMLElement>()
const dropdownRef = ref<HTMLElement>()
const isOpen = ref(false)
const dropdownStyle = ref({})

const displayValue = computed(() => {
  const selected = props.options.find(opt => opt.value === props.modelValue)
  return selected ? selected.label : (props.placeholder || '请选择')
})

function toggleDropdown() {
  if (props.disabled) return
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    updateDropdownPosition()
  }
}

function selectOption(option: SelectOption) {
  emit('update:modelValue', option.value)
  isOpen.value = false
}

function updateDropdownPosition() {
  if (!selectRef.value) return

  const rect = selectRef.value.getBoundingClientRect()
  const dropdownHeight = Math.min(props.options.length * 40, 240)

  // 检查下方空间是否足够
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top

  if (spaceBelow >= dropdownHeight || spaceBelow >= spaceAbove) {
    // 下拉显示
    dropdownStyle.value = {
      top: `${rect.bottom + 4}px`,
      left: `${rect.left}px`,
      width: `${rect.width}px`,
      maxHeight: `${Math.min(dropdownHeight, spaceBelow - 8)}px`
    }
  } else {
    // 上拉显示
    dropdownStyle.value = {
      bottom: `${window.innerHeight - rect.top + 4}px`,
      left: `${rect.left}px`,
      width: `${rect.width}px`,
      maxHeight: `${Math.min(dropdownHeight, spaceAbove - 8)}px`
    }
  }
}

function handleClickOutside(event: MouseEvent) {
  if (
    isOpen.value &&
    selectRef.value &&
    dropdownRef.value &&
    !selectRef.value.contains(event.target as Node) &&
    !dropdownRef.value.contains(event.target as Node)
  ) {
    isOpen.value = false
  }
}

watch(isOpen, (newVal) => {
  if (newVal) {
    document.addEventListener('click', handleClickOutside, true)
  } else {
    document.removeEventListener('click', handleClickOutside, true)
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside, true)
})
</script>

<style scoped>
.custom-select {
  position: relative;
  width: 100%;
}

.select-trigger {
  width: 100%;
  height: 40px;
  padding: var(--sp-12) var(--sp-16);
  padding-right: var(--sp-32);
  font-size: 14px;
  color: var(--color-text-primary);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: space-between;
  user-select: none;
}

.select-trigger:hover {
  border-color: var(--color-primary);
}

.select-trigger.is-open {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(27, 170, 127, 0.1);
}

.select-trigger.is-disabled {
  background: var(--color-bg-3);
  color: var(--color-text-disabled);
  cursor: not-allowed;
}

.select-trigger.is-disabled:hover {
  border-color: var(--color-border);
}

.select-value {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.select-arrow {
  width: 12px;
  height: 12px;
  margin-left: var(--sp-8);
  transition: transform 0.2s;
  flex-shrink: 0;
  color: var(--color-text-tertiary);
}

.select-trigger.is-open .select-arrow {
  transform: rotate(180deg);
}

.select-dropdown {
  position: fixed;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-8);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  overflow-y: auto;
  z-index: 9999;
}

.select-option {
  padding: var(--sp-10) var(--sp-16);
  font-size: 14px;
  color: var(--color-text-primary);
  cursor: pointer;
  transition: all 0.2s;
}

.select-option:hover {
  background: var(--color-bg-2);
}

.select-option.is-selected {
  background: rgba(27, 170, 127, 0.1);
  color: var(--color-primary);
  font-weight: 500;
}
</style>
