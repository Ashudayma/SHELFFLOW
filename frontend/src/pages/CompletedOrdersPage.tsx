import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { Navigate, useNavigate } from 'react-router-dom';
import { pickerApi } from '../api/api';
import { apiErrorMessage, statusCode } from '../api/http';

export function CompletedOrdersPage() {
  const navigate = useNavigate();
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: pickerApi.dashboard });

  if (dashboard.isError && statusCode(dashboard.error) === 409) {
    return <Navigate to="/picker/select-warehouse" replace />;
  }

  const completed = dashboard.data?.completed ?? [];

  return (
    <Stack spacing={2}>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/picker')} sx={{ alignSelf: 'flex-start' }}>
        Back to dashboard
      </Button>
      <Typography variant="h4">Completed orders</Typography>

      {dashboard.isError && <Alert severity="error">{apiErrorMessage(dashboard.error)}</Alert>}
      {dashboard.isLoading && <Typography>Loading…</Typography>}

      {dashboard.data && completed.length === 0 && (
        <Alert severity="info">You have not completed any orders in this warehouse yet.</Alert>
      )}

      {completed.length > 0 && (
        <Paper>
          <List disablePadding>
            {completed.map((o) => (
              <ListItemButton key={o.id} onClick={() => navigate(`/picker/orders/${o.id}/pick`)} divider>
                <ListItemIcon>
                  <CheckCircleIcon color="success" />
                </ListItemIcon>
                <ListItemText
                  primary={<Typography variant="h6">{o.orderNumber}</Typography>}
                  secondary={`Customer ${o.customerId}`}
                />
                <ChevronRightIcon />
              </ListItemButton>
            ))}
          </List>
        </Paper>
      )}
    </Stack>
  );
}
