import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { productsApi, usersApi, warehousesApi } from '../api/api';
import { PageHeader } from '../components/PageHeader';

function StatCard({ label, value, to }: { label: string; value: number | string; to: string }) {
  const navigate = useNavigate();
  return (
    <Card>
      <CardActionArea onClick={() => navigate(to)}>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            {label}
          </Typography>
          <Typography variant="h4">{value}</Typography>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

export function DashboardPage() {
  const users = useQuery({ queryKey: ['users'], queryFn: usersApi.list });
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  const products = useQuery({ queryKey: ['products'], queryFn: productsApi.list });

  const dash = (v: number | undefined) => (v == null ? '—' : v);
  const pickerCount = users.data?.filter((u) => u.role === 'HUB_PICKER').length;

  return (
    <>
      <PageHeader title="Dashboard" />
      <Grid container spacing={2}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard label="Users" value={dash(users.data?.length)} to="/admin/users" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard label="Hub Pickers" value={dash(pickerCount)} to="/admin/users" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard label="Warehouses" value={dash(warehouses.data?.length)} to="/admin/warehouses" />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard label="Products" value={dash(products.data?.length)} to="/admin/products" />
        </Grid>
      </Grid>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>
        Use the navigation to manage users and warehouses, maintain inventory and shelf mappings,
        upload daily orders, and download dispatch reports.
      </Typography>
    </>
  );
}
