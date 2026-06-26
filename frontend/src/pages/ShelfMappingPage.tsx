import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControl from '@mui/material/FormControl';
import Grid from '@mui/material/Grid';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { productsApi, shelfApi, warehousesApi } from '../api/api';
import { apiErrorMessage } from '../api/http';
import { PageHeader } from '../components/PageHeader';

const LOCATION_HINT = 'Format: Aisle_A-Bay_04-Shelf_2';

export function ShelfMappingPage() {
  const queryClient = useQueryClient();
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  const products = useQuery({ queryKey: ['products'], queryFn: productsApi.list });

  const [warehouseId, setWarehouseId] = useState<number | ''>('');
  const [sku, setSku] = useState('');
  const [locationCode, setLocationCode] = useState('');
  const [feedback, setFeedback] = useState<{ severity: 'success' | 'error'; message: string } | null>(null);

  const shelves = useQuery({
    queryKey: ['shelf-locations', warehouseId],
    queryFn: () => shelfApi.listByWarehouse(warehouseId as number),
    enabled: warehouseId !== '',
  });

  // Two mutations sharing payload, differing by verb (POST create / PUT update).
  const createMutation = useMutation({
    mutationFn: () => shelfApi.create({ warehouseId: warehouseId as number, sku, locationCode }),
    onSuccess: () => onSaved('Mapping created.'),
    onError: (err) => setFeedback({ severity: 'error', message: apiErrorMessage(err) }),
  });
  const updateMutation = useMutation({
    mutationFn: () => shelfApi.update({ warehouseId: warehouseId as number, sku, locationCode }),
    onSuccess: () => onSaved('Mapping updated.'),
    onError: (err) => setFeedback({ severity: 'error', message: apiErrorMessage(err) }),
  });

  function onSaved(message: string) {
    setFeedback({ severity: 'success', message });
    queryClient.invalidateQueries({ queryKey: ['shelf-locations', warehouseId] });
  }

  const canSubmit = warehouseId !== '' && sku !== '' && locationCode !== '';

  return (
    <>
      <PageHeader title="Shelf Mapping" />
      <Grid container spacing={2}>
        <Grid item xs={12} md={5}>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Map a SKU to a location
            </Typography>
            <Stack spacing={2}>
              {feedback && <Alert severity={feedback.severity}>{feedback.message}</Alert>}
              <FormControl fullWidth>
                <InputLabel id="wh">Warehouse</InputLabel>
                <Select
                  labelId="wh"
                  label="Warehouse"
                  value={warehouseId}
                  onChange={(e) => {
                    setWarehouseId(e.target.value as number);
                    setFeedback(null);
                  }}
                >
                  {warehouses.data?.map((w) => (
                    <MenuItem key={w.id} value={w.id}>
                      {w.warehouseCode} — {w.warehouseName}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl fullWidth>
                <InputLabel id="sku">SKU</InputLabel>
                <Select labelId="sku" label="SKU" value={sku} onChange={(e) => setSku(e.target.value)}>
                  {products.data?.map((p) => (
                    <MenuItem key={p.sku} value={p.sku}>
                      {p.sku} — {p.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                label="Location code"
                value={locationCode}
                onChange={(e) => setLocationCode(e.target.value)}
                helperText={LOCATION_HINT}
                placeholder="Aisle_A-Bay_04-Shelf_2"
              />
              <Stack direction="row" spacing={1}>
                <Button
                  variant="contained"
                  onClick={() => {
                    setFeedback(null);
                    createMutation.mutate();
                  }}
                  disabled={!canSubmit || createMutation.isPending}
                >
                  Create
                </Button>
                <Button
                  variant="outlined"
                  onClick={() => {
                    setFeedback(null);
                    updateMutation.mutate();
                  }}
                  disabled={!canSubmit || updateMutation.isPending}
                >
                  Update existing
                </Button>
              </Stack>
            </Stack>
          </Paper>
        </Grid>

        <Grid item xs={12} md={7}>
          <Paper>
            <Typography variant="h6" sx={{ p: 2, pb: 1 }}>
              Mappings {warehouseId !== '' ? 'in selected warehouse' : ''}
            </Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>SKU</TableCell>
                    <TableCell>Aisle</TableCell>
                    <TableCell>Bay</TableCell>
                    <TableCell>Shelf</TableCell>
                    <TableCell>Location code</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {warehouseId === '' && (
                    <TableRow><TableCell colSpan={5}>Select a warehouse to view mappings.</TableCell></TableRow>
                  )}
                  {warehouseId !== '' && shelves.isLoading && (
                    <TableRow><TableCell colSpan={5}>Loading…</TableCell></TableRow>
                  )}
                  {shelves.data?.map((s) => (
                    <TableRow
                      key={s.id}
                      hover
                      onClick={() => {
                        setSku(s.sku);
                        setLocationCode(s.locationCode);
                      }}
                      sx={{ cursor: 'pointer' }}
                    >
                      <TableCell>{s.sku}</TableCell>
                      <TableCell>{s.aisle ?? '—'}</TableCell>
                      <TableCell>{s.bay ?? '—'}</TableCell>
                      <TableCell>{s.shelf ?? '—'}</TableCell>
                      <TableCell>{s.locationCode}</TableCell>
                    </TableRow>
                  ))}
                  {warehouseId !== '' && shelves.data?.length === 0 && (
                    <TableRow><TableCell colSpan={5}>No mappings in this warehouse yet.</TableCell></TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </Grid>
      </Grid>
    </>
  );
}
