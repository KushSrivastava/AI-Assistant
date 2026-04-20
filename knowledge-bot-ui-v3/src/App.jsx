import React from 'react';
import { useWorkspace } from './context/WorkspaceContext.jsx';
import { StartupScreen } from './components/startup/StartupScreen.jsx';
import { AppShell } from './components/layout/AppShell.jsx';

function App() {
  const { isAttached } = useWorkspace();
  return isAttached ? <AppShell /> : <StartupScreen />;
}

export default App;
