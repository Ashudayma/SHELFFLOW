import WarehouseIcon from '@mui/icons-material/Warehouse';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { pickerApi } from '../api/api';
import { apiErrorMessage } from '../api/http';

export function SelectWarehousePage() {
  const navigate = useNavigate();
  const warehouses = useQuery({ queryKey: ['my-warehouses'], queryFn: pickerApi.myWarehouses });

  const select = useMutation({
    mutationFn: (warehouseId: number) => pickerApi.selectWarehouse(warehouseId),
    onSuccess: () => navigate('/picker', { replace: true }),
  });

  return (
    <Stack spacing={2}>
      <Typography variant="h4">Select your warehouse</Typography>
      <Typography variant="body1" color="text.secondary">
        Choose the warehouse you are working in today.
      </Typography>

      {warehouses.isError && <Alert severity="error">{apiErrorMessage(warehouses.error)}</Alert>}
      {select.isError && <Alert severity="error">{apiErrorMessage(select.error)}</Alert>}
      {warehouses.isLoading && <Typography>Loading warehouses…</Typography>}
      {warehouses.data?.length === 0 && (
        <Alert severity="warning">
          You are not assigned to any warehouse yet. Ask a Central Admin to map you.
        </Alert>
      )}

      <Stack spacing={2}>
        {warehouses.data?.map((w) => (
          <Button
            key={w.id}
            variant="contained"
            size="large"
            startIcon={<WarehouseIcon />}
            onClick={() => select.mutate(w.id)}
            disabled={select.isPending}
            sx={{ justifyContent: 'flex-start', py: 2, fontSize: '1.3rem' }}
          >
            {w.warehouseCode} — {w.warehouseName}
          </Button>
        ))}
      </Stack>
    </Stack>
  );
}
