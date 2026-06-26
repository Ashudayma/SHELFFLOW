import BlockIcon from '@mui/icons-material/Block';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/**
 * 403 page shown when an authenticated user hits an area their role can't access — mirrors the
 * backend's RBAC 403. Offers a way back to the user's own dashboard.
 */
export function ForbiddenPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const home = user?.role === 'CENTRAL_ADMIN' ? '/admin' : '/picker';

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 3,
        bgcolor: 'background.default',
      }}
    >
      <Stack spacing={2} alignItems="center" textAlign="center">
        <BlockIcon color="error" sx={{ fontSize: 64 }} />
        <Typography variant="h4">403 — Access denied</Typography>
        <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 420 }}>
          Your role ({user?.role ?? 'unknown'}) doesn't have access to this area.
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button variant="contained" onClick={() => navigate(home, { replace: true })}>
            Go to my dashboard
          </Button>
          <Button variant="outlined" onClick={() => void logout()}>
            Log out
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
}
