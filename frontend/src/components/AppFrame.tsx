import LogoutIcon from '@mui/icons-material/Logout';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';

export function AppFrame({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky">
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 800 }}>
            ShelfLife Picker
          </Typography>
          {user && (
            <Typography variant="body2" sx={{ mr: 2, display: { xs: 'none', sm: 'block' } }}>
              {user.email}
            </Typography>
          )}
          <Button color="inherit" startIcon={<LogoutIcon />} onClick={() => void logout()}>
            Logout
          </Button>
        </Toolbar>
      </AppBar>
      <Container maxWidth="sm" sx={{ py: 2 }}>
        {children}
      </Container>
    </Box>
  );
}
