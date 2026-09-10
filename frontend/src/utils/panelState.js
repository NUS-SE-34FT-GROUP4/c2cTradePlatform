// Simple event bus for inter-component communication
import { ref } from 'vue';

const panelState = ref({
  activePanel: null // 'cart', 'favorites', or null
});

export const usePanelState = () => {
  const setActivePanel = (panel) => {
    panelState.value.activePanel = panel;
  };

  const clearActivePanel = () => {
    panelState.value.activePanel = null;
  };

  const isActivePanel = (panel) => {
    return panelState.value.activePanel === panel;
  };

  return {
    panelState,
    setActivePanel,
    clearActivePanel,
    isActivePanel
  };
};

