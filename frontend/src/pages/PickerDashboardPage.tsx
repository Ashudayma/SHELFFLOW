import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { Navigate, useNavigate } from 'react-router-dom';
import { pickerApi } from '../api/api';
import { apiErrorMessage, statusCode } from '../api/http';
import type { OrderSummary } from '../types';

function OrderSection({
  title,
  orders,
  emptyText,
  onOpen,
}: {
  title: string;
  orders: OrderSummary[];
  emptyText: string;
  onOpen: (order: OrderSummary) => void;
}) {
  return (
    <Paper sx={{ p: 1.5 }}>
      <Typography variant="h6" sx={{ px: 1, py: 0.5 }}>
        {title} ({orders.length})
      </Typography>
      {orders.length === 0 ? (
        <Typography sx={{ px: 1, py: 1 }} color="text.secondary">
          {emptyText}
        </Typography>
      ) : (
        <List disablePadding>
          {orders.map((o) => (
            <ListItemButton key={o.id} onClick={() => onOpen(o)} divider>
              <ListItemText
                primary={<Typography variant="h6">{o.orderNumber}</Typography>}
                secondary={`Customer ${o.customerId}`}
              />
              <Chip label={o.status} />
              <ChevronRightIcon sx={{ ml: 1 }} />
            </ListItemButton>
          ))}
        </List>
      )}
    </Paper>
  );
}

export function PickerDashboardPage() {
  const navigate = useNavigate();
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: pickerApi.dashboard });

  // No active warehouse selected yet → go register one (Warehouse Entry).
  if (dashboard.isError && statusCode(dashboard.error) === 409) {
    return <Navigate to="/picker/select-warehouse" replace />;
  }

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4">Dashboard</Typography>
        <Button
          variant="outlined"
          startIcon={<SwapHorizIcon />}
          onClick={() => navigate('/picker/select-warehouse')}
        >
          Change warehouse
        </Button>
      </Box>

      {dashboard.isError && <Alert severity="error">{apiErrorMessage(dashboard.error)}</Alert>}
      {dashboard.isLoading && <Typography>Loading…</Typography>}

      {dashboard.data && (
        <>
          <OrderSection
            title="Available to claim"
            orders={dashboard.data.available}
            emptyText="No unclaimed orders right now."
            onOpen={(o) => navigate(`/picker/orders/${o.id}/claim`)}
          />
          <OrderSection
            title="In progress"
            orders={dashboard.data.current}
            emptyText="You have no orders in progress."
            onOpen={(o) => navigate(`/picker/orders/${o.id}/pick`)}
          />
          <Button variant="text" size="large" onClick={() => navigate('/picker/completed')}>
            View completed orders ({dashboard.data.completed.length})
          </Button>
        </>
      )}
    </Stack>
  );
}
