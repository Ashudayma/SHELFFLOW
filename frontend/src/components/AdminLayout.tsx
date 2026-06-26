import AssessmentIcon from '@mui/icons-material/Assessment';
import DashboardIcon from '@mui/icons-material/Dashboard';
import GridViewIcon from '@mui/icons-material/GridView';
import HomeIcon from '@mui/icons-material/Home';
import Inventory2Icon from '@mui/icons-material/Inventory2';
import LogoutIcon from '@mui/icons-material/Logout';
import PeopleIcon from '@mui/icons-material/People';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import WarehouseIcon from '@mui/icons-material/Warehouse';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const DRAWER_WIDTH = 230;

const NAV = [
  { to: '/admin', label: 'Dashboard', icon: <DashboardIcon />, end: true },
  { to: '/admin/users', label: 'Users', icon: <PeopleIcon /> },
  { to: '/admin/warehouses', label: 'Warehouses', icon: <WarehouseIcon /> },
  { to: '/admin/products', label: 'Products', icon: <Inventory2Icon /> },
  { to: '/admin/shelf-mapping', label: 'Shelf Mapping', icon: <GridViewIcon /> },
  { to: '/admin/upload-orders', label: 'Upload Orders', icon: <UploadFileIcon /> },
  { to: '/admin/reports', label: 'Reports', icon: <AssessmentIcon /> },
];

export function AdminLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 2 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            ShelfLife Admin
          </Typography>
          {user && (
            <Typography variant="body2" sx={{ opacity: 0.9 }}>
              {user.email}
            </Typography>
          )}
          <Button color="inherit" startIcon={<LogoutIcon />} onClick={() => void logout()}>
            Logout
          </Button>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: 'auto' }}>
          <List>
            <ListItemButton component={NavLink} to="/">
              <ListItemIcon><HomeIcon /></ListItemIcon>
              <ListItemText primary="Home" />
            </ListItemButton>
            {NAV.map((item) => {
              const selected = item.end
                ? location.pathname === item.to
                : location.pathname.startsWith(item.to);
              return (
                <ListItemButton key={item.to} component={NavLink} to={item.to} selected={selected}>
                  <ListItemIcon>{item.icon}</ListItemIcon>
                  <ListItemText primary={item.label} />
                </ListItemButton>
              );
            })}
          </List>
        </Box>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3, minHeight: '100vh' }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
