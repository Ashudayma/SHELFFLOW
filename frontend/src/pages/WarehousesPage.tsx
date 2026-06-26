import AddIcon from '@mui/icons-material/Add';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { warehousesApi } from '../api/api';
import { apiErrorMessage } from '../api/http';
import { PageHeader } from '../components/PageHeader';
import type { CreateWarehouseRequest } from '../types';

export function WarehousesPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<CreateWarehouseRequest>({ warehouseCode: '', warehouseName: '', address: '' });
  const [formError, setFormError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: () => warehousesApi.create(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['warehouses'] });
      setOpen(false);
    },
    onError: (err) => setFormError(apiErrorMessage(err)),
  });

  const openDialog = () => {
    setForm({ warehouseCode: '', warehouseName: '', address: '' });
    setFormError(null);
    setOpen(true);
  };

  return (
    <>
      <PageHeader
        title="Warehouses"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={openDialog}>
            New warehouse
          </Button>
        }
      />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(error)}</Alert>}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Code</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Address</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow><TableCell colSpan={4}>Loading…</TableCell></TableRow>
            )}
            {data?.map((w) => (
              <TableRow key={w.id} hover>
                <TableCell>{w.id}</TableCell>
                <TableCell>{w.warehouseCode}</TableCell>
                <TableCell>{w.warehouseName}</TableCell>
                <TableCell>{w.address ?? '—'}</TableCell>
              </TableRow>
            ))}
            {data?.length === 0 && (
              <TableRow><TableCell colSpan={4}>No warehouses yet.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New warehouse</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              label="Warehouse code"
              value={form.warehouseCode}
              onChange={(e) => setForm({ ...form, warehouseCode: e.target.value })}
              required
            />
            <TextField
              label="Warehouse name"
              value={form.warehouseName}
              onChange={(e) => setForm({ ...form, warehouseName: e.target.value })}
              required
            />
            <TextField
              label="Address"
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
              multiline
              minRows={2}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              setFormError(null);
              createMutation.mutate();
            }}
            disabled={createMutation.isPending || !form.warehouseCode || !form.warehouseName}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
