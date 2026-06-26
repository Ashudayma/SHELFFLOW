import { createTheme } from '@mui/material/styles';

// Admin console theme.
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#1f6f43' },
    secondary: { main: '#b5651d' },
    background: { default: '#f5f6f8' },
  },
  shape: { borderRadius: 8 },
  typography: {
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
  },
});

// Picker theme (BRD §4 rugged-device UI/UX): high contrast, large fonts, ≥44px touch targets.
// Applied only to the /picker subtree via a nested ThemeProvider in PickerLayout.
const MIN_TOUCH = 48;

export const pickerTheme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#0b6b3a', contrastText: '#ffffff' },
    secondary: { main: '#8a5a00', contrastText: '#ffffff' },
    error: { main: '#b00020', contrastText: '#ffffff' },
    success: { main: '#0b6b3a', contrastText: '#ffffff' },
    warning: { main: '#8a5a00', contrastText: '#ffffff' },
    text: { primary: '#0a0a0a', secondary: '#333333' },
    background: { default: '#ffffff', paper: '#ffffff' },
  },
  typography: {
    fontSize: 16,
    htmlFontSize: 16,
    button: { fontSize: '1.15rem', fontWeight: 800, textTransform: 'none' },
    h4: { fontWeight: 800 },
    h5: { fontWeight: 800 },
    h6: { fontWeight: 800 },
    body1: { fontSize: '1.1rem' },
  },
  shape: { borderRadius: 10 },
  components: {
    MuiButton: {
      defaultProps: { size: 'large' },
      styleOverrides: { root: { minHeight: 56, paddingInline: 20 } },
    },
    MuiIconButton: {
      styleOverrides: { root: { minWidth: MIN_TOUCH, minHeight: MIN_TOUCH } },
    },
    MuiListItemButton: {
      styleOverrides: { root: { minHeight: 64, borderRadius: 10 } },
    },
    MuiInputBase: {
      styleOverrides: { root: { minHeight: 56, fontSize: '1.2rem' } },
    },
    MuiChip: {
      styleOverrides: { root: { fontSize: '0.95rem', height: 30, fontWeight: 700 } },
    },
  },
});
