<template>
  <Teleport to="body">
    <Transition name="dialog-fade">
      <div v-if="dialogState.visible" class="dialog-overlay" @click.self="handleCancel">
        <div class="dialog-container">
          <div class="dialog-header">
            <div class="dialog-icon" :class="`icon-${dialogState.type}`">
              <svg v-if="dialogState.type === 'warning'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              <svg v-else-if="dialogState.type === 'error'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="15" y1="9" x2="9" y2="15"/>
                <line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              <svg v-else-if="dialogState.type === 'success'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9 12l2 2 4-4"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
            </div>
            <h3 class="dialog-title">{{ dialogState.title }}</h3>
          </div>

          <div class="dialog-body">
            <p class="dialog-message">{{ dialogState.message }}</p>
          </div>

          <div class="dialog-footer">
            <button
              v-if="dialogState.showCancel"
              @click="handleCancel"
              class="dialog-btn dialog-btn-cancel"
            >
              {{ dialogState.cancelText }}
            </button>
            <button
              @click="handleConfirm"
              class="dialog-btn dialog-btn-confirm"
              :class="`confirm-${dialogState.type}`"
            >
              {{ dialogState.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { useDialog } from '../composables/useDialog'

const { dialogState, handleConfirm, handleCancel } = useDialog()
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(4px);
}

.dialog-container {
  background: var(--color-bg-1);
  border-radius: var(--radius-16);
  width: 90%;
  max-width: 420px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2), 0 10px 10px -5px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.dialog-header {
  padding: var(--sp-24) var(--sp-24) var(--sp-16);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-12);
}

.dialog-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog-icon svg {
  width: 28px;
  height: 28px;
}

.icon-warning {
  background: #FEF3C7;
  color: #D97706;
}

.icon-error {
  background: #FEE2E2;
  color: #DC2626;
}

.icon-success {
  background: #D1FAE5;
  color: #059669;
}

.icon-info {
  background: #DBEAFE;
  color: #2563EB;
}

.dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
  text-align: center;
}

.dialog-body {
  padding: 0 var(--sp-24) var(--sp-24);
}

.dialog-message {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  text-align: center;
  margin: 0;
}

.dialog-footer {
  display: flex;
  gap: var(--sp-12);
  padding: var(--sp-20) var(--sp-24) var(--sp-24);
}

.dialog-btn {
  flex: 1;
  height: 40px;
  padding: 0 var(--sp-20);
  border: none;
  border-radius: var(--radius-8);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.dialog-btn-cancel {
  background: var(--color-bg-2);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}

.dialog-btn-cancel:hover {
  background: var(--color-border);
}

.dialog-btn-confirm {
  color: white;
}

.confirm-warning {
  background: #D97706;
}

.confirm-warning:hover {
  background: #B45309;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.3);
}

.confirm-error {
  background: #DC2626;
}

.confirm-error:hover {
  background: #B91C1C;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.3);
}

.confirm-success {
  background: #059669;
}

.confirm-success:hover {
  background: #047857;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(5, 150, 105, 0.3);
}

.confirm-info {
  background: var(--color-primary);
}

.confirm-info:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(27, 170, 127, 0.3);
}

.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: opacity 0.2s ease;
}

.dialog-fade-enter-active .dialog-container,
.dialog-fade-leave-active .dialog-container {
  transition: all 0.2s ease;
}

.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}

.dialog-fade-enter-from .dialog-container,
.dialog-fade-leave-to .dialog-container {
  transform: scale(0.95) translateY(-20px);
}
</style>
