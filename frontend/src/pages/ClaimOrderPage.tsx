import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { ordersApi } from '../api/api';
import { apiErrorMessage } from '../api/http';

export function ClaimOrderPage() {
  const navigate = useNavigate();
  const orderId = Number(useParams().id);

  const route = useQuery({
    queryKey: ['route', orderId],
    queryFn: () => ordersApi.route(orderId),
    enabled: Number.isFinite(orderId),
  });

  const claim = useMutation({
    mutationFn: () => ordersApi.claim(orderId),
    onSuccess: () => navigate(`/picker/orders/${orderId}/pick`, { replace: true }),
  });

  return (
    <Stack spacing={2}>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/picker')} sx={{ alignSelf: 'flex-start' }}>
        Back
      </Button>
      <Typography variant="h4">Claim order</Typography>

      {route.isError && <Alert severity="error">{apiErrorMessage(route.error)}</Alert>}
      {claim.isError && <Alert severity="error">{apiErrorMessage(claim.error)}</Alert>}

      {route.data && (
        <Paper sx={{ p: 2 }}>
          <Typography variant="h5" gutterBottom>
            {route.data.orderNumber}
          </Typography>
          <Typography variant="body1" color="text.secondary" gutterBottom>
            {route.data.steps.length} item(s) to pick:
          </Typography>
          <List dense>
            {route.data.steps.map((s) => (
              <ListItem key={s.sequence} disableGutters>
                <ListItemText
                  primary={`${s.itemName} (${s.sku})`}
                  secondary={`${s.locationCode ?? 'No location'} · qty ${s.orderedQuantity}`}
                />
              </ListItem>
            ))}
          </List>
        </Paper>
      )}

      <Button
        variant="contained"
        size="large"
        onClick={() => claim.mutate()}
        disabled={claim.isPending || route.isLoading}
        sx={{ py: 2, fontSize: '1.3rem' }}
      >
        {claim.isPending ? 'Claiming…' : 'Claim this order'}
      </Button>
    </Stack>
  );
}
