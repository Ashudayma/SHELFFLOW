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
import { productsApi } from '../api/api';
import { apiErrorMessage } from '../api/http';
import { PageHeader } from '../components/PageHeader';
import type { CreateProductRequest } from '../types';

const EMPTY: CreateProductRequest = { sku: '', name: '', barcode: '', unit: '' };

export function ProductsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useQuery({ queryKey: ['products'], queryFn: productsApi.list });
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<CreateProductRequest>(EMPTY);
  const [formError, setFormError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: () => productsApi.create(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setOpen(false);
    },
    onError: (err) => setFormError(apiErrorMessage(err)),
  });

  const openDialog = () => {
    setForm(EMPTY);
    setFormError(null);
    setOpen(true);
  };

  return (
    <>
      <PageHeader
        title="Products"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={openDialog}>
            New product
          </Button>
        }
      />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(error)}</Alert>}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>SKU</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Barcode</TableCell>
              <TableCell>Unit</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={4}>Loading…</TableCell></TableRow>}
            {data?.map((p) => (
              <TableRow key={p.sku} hover>
                <TableCell>{p.sku}</TableCell>
                <TableCell>{p.name}</TableCell>
                <TableCell>{p.barcode ?? '—'}</TableCell>
                <TableCell>{p.unit ?? '—'}</TableCell>
              </TableRow>
            ))}
            {data?.length === 0 && <TableRow><TableCell colSpan={4}>No products yet.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New product</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              label="SKU"
              value={form.sku}
              onChange={(e) => setForm({ ...form, sku: e.target.value })}
              required
            />
            <TextField
              label="Name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
            <TextField
              label="Barcode"
              value={form.barcode}
              onChange={(e) => setForm({ ...form, barcode: e.target.value })}
            />
            <TextField
              label="Unit"
              value={form.unit}
              onChange={(e) => setForm({ ...form, unit: e.target.value })}
              placeholder="EA"
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
            disabled={createMutation.isPending || !form.sku || !form.name}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
