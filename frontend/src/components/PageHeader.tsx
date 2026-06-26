import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

export function PageHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
      <Typography variant="h5">{title}</Typography>
      {action}
    </Box>
  );
}
