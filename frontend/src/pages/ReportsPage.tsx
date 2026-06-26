import DownloadIcon from '@mui/icons-material/Download';
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
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { reportApi, usersApi, warehousesApi } from '../api/api';
import { apiErrorMessage } from '../api/http';
import { PageHeader } from '../components/PageHeader';
import type { ReportFilters, ReportFormat } from '../types';

export function ReportsPage() {
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  const users = useQuery({ queryKey: ['users'], queryFn: usersApi.list });
  const pickers = users.data?.filter((u) => u.role === 'HUB_PICKER') ?? [];

  const [date, setDate] = useState('');
  const [warehouse, setWarehouse] = useState('');
  const [picker, setPicker] = useState<number | ''>('');
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<ReportFormat | null>(null);

  const filters: ReportFilters = {
    date: date || undefined,
    warehouse: warehouse || undefined,
    picker: picker === '' ? undefined : picker,
  };

  const report = useQuery({
    queryKey: ['report', filters],
    queryFn: () => reportApi.json(filters),
  });

  const triggerDownload = async (format: ReportFormat) => {
    setDownloadError(null);
    setDownloading(format);
    try {
      const response = await reportApi.download(filters, format);
      const url = URL.createObjectURL(response.data);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `dispatch-report.${format}`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setDownloadError(apiErrorMessage(err, 'Download failed.'));
    } finally {
      setDownloading(null);
    }
  };

  const rate = (value: number) => `${(value * 100).toFixed(1)}%`;

  return (
    <>
      <PageHeader title="Dispatch Report" />

      <Paper sx={{ p: 2, mb: 2 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} sm={6} md={3}>
            <TextField
              label="Date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <FormControl fullWidth>
              <InputLabel id="wh">Warehouse</InputLabel>
              <Select
                labelId="wh"
                label="Warehouse"
                value={warehouse}
                onChange={(e) => setWarehouse(e.target.value)}
              >
                <MenuItem value="">All</MenuItem>
                {warehouses.data?.map((w) => (
                  <MenuItem key={w.id} value={w.warehouseCode}>
                    {w.warehouseCode} — {w.warehouseName}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <FormControl fullWidth>
              <InputLabel id="pk">Picker</InputLabel>
              <Select
                labelId="pk"
                label="Picker"
                value={picker}
                onChange={(e) => setPicker(e.target.value === '' ? '' : Number(e.target.value))}
              >
                <MenuItem value="">All</MenuItem>
                {pickers.map((p) => (
                  <MenuItem key={p.id} value={p.id}>
                    {p.name} (#{p.id})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <Stack direction="row" spacing={1}>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void triggerDownload('csv')}
                disabled={downloading !== null}
              >
                CSV
              </Button>
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void triggerDownload('xlsx')}
                disabled={downloading !== null}
              >
                Excel
              </Button>
            </Stack>
          </Grid>
        </Grid>
      </Paper>

      {downloadError && <Alert severity="error" sx={{ mb: 2 }}>{downloadError}</Alert>}
      {report.isError && <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(report.error)}</Alert>}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Order_ID</TableCell>
              <TableCell>Picker_ID</TableCell>
              <TableCell>Warehouse_ID</TableCell>
              <TableCell>Item_SKU</TableCell>
              <TableCell>Item_Name</TableCell>
              <TableCell align="right">Qty Ordered</TableCell>
              <TableCell align="right">Qty Picked</TableCell>
              <TableCell align="right">Fulfillment</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {report.isLoading && <TableRow><TableCell colSpan={8}>Loading…</TableCell></TableRow>}
            {report.data?.map((r, idx) => (
              <TableRow key={`${r.Order_ID}-${r.Item_SKU}-${idx}`} hover>
                <TableCell>{r.Order_ID}</TableCell>
                <TableCell>{r.Picker_ID ?? '—'}</TableCell>
                <TableCell>{r.Warehouse_ID}</TableCell>
                <TableCell>{r.Item_SKU}</TableCell>
                <TableCell>{r.Item_Name}</TableCell>
                <TableCell align="right">{r.Quantity_Ordered}</TableCell>
                <TableCell align="right">{r.Quantity_Picked}</TableCell>
                <TableCell align="right">{rate(r.Fulfillment_Rate)}</TableCell>
              </TableRow>
            ))}
            {report.data?.length === 0 && (
              <TableRow><TableCell colSpan={8}>No rows match the selected filters.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </>
  );
}
