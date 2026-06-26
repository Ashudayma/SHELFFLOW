import { ThemeProvider } from '@mui/material/styles';
import { Outlet } from 'react-router-dom';
import { pickerTheme } from '../theme';
import { AppFrame } from './AppFrame';

/**
 * Wraps the /picker subtree in the high-contrast picker theme (BRD §4) and the picker app frame.
 */
export function PickerLayout() {
  return (
    <ThemeProvider theme={pickerTheme}>
      <AppFrame>
        <Outlet />
      </AppFrame>
    </ThemeProvider>
  );
}
