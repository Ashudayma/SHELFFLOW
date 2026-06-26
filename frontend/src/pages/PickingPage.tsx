import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import QrCodeScannerIcon from '@mui/icons-material/QrCodeScanner';
import SkipNextIcon from '@mui/icons-material/SkipNext';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ordersApi, scanApi } from '../api/api';
import { apiErrorMessage } from '../api/http';
import type { OrderItemStatus, RouteStep } from '../types';

type Feedback = { severity: 'success' | 'error' | 'info'; message: string } | null;

const STATUS_CHIP: Record<OrderItemStatus, { label: string; color: 'success' | 'warning' | 'default' }> = {
  PICKED: { label: 'Picked', color: 'success' },
  SKIPPED: { label: 'Skipped', color: 'warning' },
  PENDING: { label: 'Pending', color: 'default' },
};

function findCurrentStep(steps: RouteStep[]): RouteStep | null {
  return (
    steps.find((s) => s.status === 'PENDING') ?? steps.find((s) => s.status === 'SKIPPED') ?? null
  );
}

export function PickingPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const orderId = Number(useParams().id);

  const [scanValue, setScanValue] = useState('');
  const [feedback, setFeedback] = useState<Feedback>(null);

  const route = useQuery({
    queryKey: ['route', orderId],
    queryFn: () => ordersApi.route(orderId),
    enabled: Number.isFinite(orderId),
  });

  const steps = route.data?.steps ?? [];
  const currentStep = findCurrentStep(steps);
  const pickedCount = steps.filter((s) => s.status === 'PICKED').length;
  const allPicked = steps.length > 0 && pickedCount === steps.length;

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['route', orderId] });

  const scan = useMutation({
    mutationFn: (sku: string) => scanApi.scan(orderId, sku),
    onSuccess: (res) => {
      setScanValue('');
      if (res.duplicate) {
        setFeedback({ severity: 'info', message: 'Already counted (duplicate scan ignored).' });
      } else if (res.orderStatus === 'COMPLETED') {
        setFeedback({ severity: 'success', message: 'Final item picked — order complete!' });
      } else {
        setFeedback({ severity: 'success', message: `Picked ${res.item?.sku ?? ''}.` });
      }
      void refresh();
    },
    onError: (err) => setFeedback({ severity: 'error', message: apiErrorMessage(err) }),
  });

  const skip = useMutation({
    mutationFn: (itemId: number) => scanApi.skip(orderId, itemId),
    onSuccess: () => {
      setFeedback({ severity: 'info', message: 'Item skipped — you can return to it later.' });
      void refresh();
    },
    onError: (err) => setFeedback({ severity: 'error', message: apiErrorMessage(err) }),
  });

  const submitScan = (sku: string) => {
    const value = sku.trim();
    if (value) scan.mutate(value);
  };

  const busy = scan.isPending || skip.isPending;

  return (
    <Stack spacing={2}>
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/picker')} sx={{ alignSelf: 'flex-start' }}>
        Back to dashboard
      </Button>

      {route.isError && <Alert severity="error">{apiErrorMessage(route.error)}</Alert>}

      {route.data && (
        <>
          <Box>
            <Typography variant="h4">{route.data.orderNumber}</Typography>
            <Typography variant="body1" color="text.secondary">
              {pickedCount} of {steps.length} items picked
            </Typography>
            <LinearProgress
              variant="determinate"
              value={steps.length ? (pickedCount / steps.length) * 100 : 0}
              sx={{ height: 12, borderRadius: 6, mt: 1 }}
            />
          </Box>

          {feedback && (
            <Alert severity={feedback.severity} sx={{ fontSize: '1.1rem', fontWeight: 600 }}>
              {feedback.message}
            </Alert>
          )}

          {allPicked ? (
            <Paper sx={{ p: 3, textAlign: 'center', border: '3px solid', borderColor: 'success.main' }}>
              <CheckCircleIcon color="success" sx={{ fontSize: 72 }} />
              <Typography variant="h4" color="success.main" sx={{ mt: 1 }}>
                Order complete
              </Typography>
              <Button
                variant="contained"
                size="large"
                sx={{ mt: 2, py: 2, fontSize: '1.2rem' }}
                onClick={() => navigate('/picker')}
                fullWidth
              >
                Back to dashboard
              </Button>
            </Paper>
          ) : (
            currentStep && (
              <Paper
                sx={{
                  p: 2.5,
                  border: '4px solid',
                  borderColor: 'primary.main',
                  bgcolor: 'rgba(11,107,58,0.06)',
                }}
              >
                <Chip label="CURRENT STEP" color="primary" sx={{ fontSize: '1rem', mb: 1 }} />
                <Typography variant="overline" color="text.secondary">
                  Go to location
                </Typography>
                <Typography variant="h4" sx={{ wordBreak: 'break-word', lineHeight: 1.2 }}>
                  {currentStep.locationCode ?? 'No mapped location'}
                </Typography>
                <Typography variant="h6" sx={{ mt: 1 }}>
                  {currentStep.itemName}
                </Typography>
                <Typography variant="body1" color="text.secondary">
                  SKU {currentStep.sku}
                </Typography>
                <Typography variant="h5" sx={{ mt: 1.5 }}>
                  Picked {currentStep.pickedQuantity} of {currentStep.orderedQuantity}
                </Typography>

                <Stack spacing={1.5} sx={{ mt: 2 }}>
                  <TextField
                    label="Simulate scan (barcode / SKU)"
                    value={scanValue}
                    onChange={(e) => setScanValue(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') submitScan(scanValue);
                    }}
                    placeholder="Type a SKU and press Scan"
                    fullWidth
                    autoFocus
                  />
                  <Stack direction="row" spacing={1.5}>
                    <Button
                      variant="contained"
                      startIcon={<QrCodeScannerIcon />}
                      onClick={() => submitScan(scanValue)}
                      disabled={busy || !scanValue.trim()}
                      sx={{ flex: 1, py: 1.5 }}
                    >
                      Scan
                    </Button>
                    <Button
                      variant="outlined"
                      onClick={() => submitScan(currentStep.sku)}
                      disabled={busy}
                      sx={{ flex: 1, py: 1.5 }}
                    >
                      Scan current item
                    </Button>
                  </Stack>
                  <Button
                    variant="text"
                    color="warning"
                    startIcon={<SkipNextIcon />}
                    onClick={() => skip.mutate(currentStep.itemId)}
                    disabled={busy}
                    sx={{ py: 1.5 }}
                  >
                    Skip this item
                  </Button>
                </Stack>
              </Paper>
            )
          )}

          <Paper sx={{ p: 1.5 }}>
            <Typography variant="h6" sx={{ px: 1, py: 0.5 }}>
              Pick route
            </Typography>
            <List disablePadding>
              {steps.map((s) => {
                const isCurrent = currentStep?.sequence === s.sequence && !allPicked;
                return (
                  <ListItem
                    key={s.sequence}
                    divider
                    sx={{
                      borderLeft: isCurrent ? '6px solid' : '6px solid transparent',
                      borderLeftColor: isCurrent ? 'primary.main' : 'transparent',
                      bgcolor: isCurrent ? 'rgba(11,107,58,0.06)' : 'transparent',
                    }}
                  >
                    <ListItemText
                      primary={
                        <Typography variant="body1" sx={{ fontWeight: isCurrent ? 800 : 500 }}>
                          {s.sequence}. {s.locationCode ?? '—'}
                        </Typography>
                      }
                      secondary={`${s.itemName} (${s.sku}) · ${s.pickedQuantity}/${s.orderedQuantity}`}
                    />
                    {isCurrent && <Chip label="CURRENT" color="primary" sx={{ mr: 1 }} />}
                    <Chip
                      label={STATUS_CHIP[s.status].label}
                      color={STATUS_CHIP[s.status].color}
                      variant="outlined"
                    />
                  </ListItem>
                );
              })}
            </List>
          </Paper>
        </>
      )}
    </Stack>
  );
}
