import UploadFileIcon from '@mui/icons-material/UploadFile';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Grid from '@mui/material/Grid';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useMutation } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { ordersApi } from '../api/api';
import { apiErrorMessage } from '../api/http';
import { PageHeader } from '../components/PageHeader';
import type { RowResultStatus } from '../types';

const STATUS_COLOR: Record<RowResultStatus, 'success' | 'error' | 'warning'> = {
  SUCCESS: 'success',
  FAILED: 'error',
  SKIPPED: 'warning',
};

function SummaryChip({ label, value, color }: { label: string; value: number; color?: 'success' | 'error' | 'warning' | 'primary' }) {
  return <Chip label={`${label}: ${value}`} color={color ?? 'default'} variant="outlined" />;
}

export function UploadOrdersPage() {
  const [file, setFile] = useState<File | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const upload = useMutation({
    mutationFn: () => ordersApi.upload(file!),
  });

  return (
    <>
      <PageHeader title="Upload Orders" />
      <Grid container spacing={2}>
        <Grid item xs={12}>
          <Paper sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
              <Button variant="outlined" component="label" startIcon={<UploadFileIcon />}>
                Choose file
                <input
                  ref={inputRef}
                  hidden
                  type="file"
                  accept=".csv,.xls,.xlsx"
                  onChange={(e) => {
                    setFile(e.target.files?.[0] ?? null);
                    upload.reset();
                  }}
                />
              </Button>
              <Typography variant="body2" sx={{ flexGrow: 1 }}>
                {file ? file.name : 'No file selected (.csv, .xls, .xlsx)'}
              </Typography>
              <Button
                variant="contained"
                disabled={!file || upload.isPending}
                onClick={() => upload.mutate()}
              >
                {upload.isPending ? 'Uploading…' : 'Upload'}
              </Button>
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Required columns: Order_ID, Customer_ID, Warehouse_ID, Item_SKU, Item_Name, Quantity_Ordered.
            </Typography>
          </Paper>
        </Grid>

        {upload.isError && (
          <Grid item xs={12}>
            <Alert severity="error">{apiErrorMessage(upload.error)}</Alert>
          </Grid>
        )}

        {upload.data && (
          <Grid item xs={12}>
            <Paper sx={{ p: 2 }}>
              <Typography variant="h6" gutterBottom>
                Ingestion report
              </Typography>
              <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }}>
                <SummaryChip label="Total rows" value={upload.data.totalRows} color="primary" />
                <SummaryChip label="Success" value={upload.data.successCount} color="success" />
                <SummaryChip label="Failed" value={upload.data.failedCount} color="error" />
                <SummaryChip label="Skipped" value={upload.data.skippedCount} color="warning" />
                <SummaryChip label="Orders created" value={upload.data.ordersCreated} color="primary" />
              </Box>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Row</TableCell>
                      <TableCell>Order_ID</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Message</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {upload.data.rows.map((r) => (
                      <TableRow key={r.row} hover>
                        <TableCell>{r.row}</TableCell>
                        <TableCell>{r.orderId || '—'}</TableCell>
                        <TableCell>
                          <Chip size="small" label={r.status} color={STATUS_COLOR[r.status]} />
                        </TableCell>
                        <TableCell sx={{ color: r.error ? 'error.main' : 'text.secondary' }}>
                          {r.error ?? 'OK'}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
          </Grid>
        )}
      </Grid>
    </>
  );
}
