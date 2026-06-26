import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import WarehouseIcon from '@mui/icons-material/Warehouse';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import OutlinedInput from '@mui/material/OutlinedInput';
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
import Tooltip from '@mui/material/Tooltip';
import InputLabel from '@mui/material/InputLabel';
import FormControl from '@mui/material/FormControl';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { mappingApi, usersApi, warehousesApi } from '../api/api';
import { apiErrorMessage } from '../api/http';
import { PageHeader } from '../components/PageHeader';
import type { Role, User, UserStatus } from '../types';

const ROLES: Role[] = ['CENTRAL_ADMIN', 'HUB_PICKER'];
const STATUSES: UserStatus[] = ['ACTIVE', 'INACTIVE', 'SUSPENDED'];

interface UserForm {
  name: string;
  email: string;
  password: string;
  role: Role;
  status: UserStatus;
}

const EMPTY_FORM: UserForm = { name: '', email: '', password: '', role: 'HUB_PICKER', status: 'ACTIVE' };

export function UsersPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useQuery({ queryKey: ['users'], queryFn: usersApi.list });

  const [mode, setMode] = useState<'create' | 'edit' | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<UserForm>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [mappingUser, setMappingUser] = useState<User | null>(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['users'] });

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (mode === 'create') {
        await usersApi.create({
          name: form.name,
          email: form.email,
          password: form.password,
          role: form.role,
          status: form.status,
        });
      } else if (mode === 'edit' && editingId != null) {
        await usersApi.update(editingId, {
          name: form.name,
          email: form.email,
          role: form.role,
          status: form.status,
          password: form.password ? form.password : undefined,
        });
      }
    },
    onSuccess: () => {
      invalidate();
      setMode(null);
    },
    onError: (err) => setFormError(apiErrorMessage(err)),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => usersApi.remove(id),
    onSuccess: invalidate,
  });

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setFormError(null);
    setMode('create');
  };

  const openEdit = (user: User) => {
    setForm({ name: user.name, email: user.email, password: '', role: user.role, status: user.status });
    setEditingId(user.id);
    setFormError(null);
    setMode('edit');
  };

  const handleDelete = (user: User) => {
    if (window.confirm(`Delete user "${user.email}"? This cannot be undone.`)) {
      deleteMutation.mutate(user.id);
    }
  };

  return (
    <>
      <PageHeader
        title="Users"
        action={
          <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
            New user
          </Button>
        }
      />
      {error && <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(error)}</Alert>}
      {deleteMutation.error && (
        <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(deleteMutation.error)}</Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && <TableRow><TableCell colSpan={6}>Loading…</TableCell></TableRow>}
            {data?.map((u) => (
              <TableRow key={u.id} hover>
                <TableCell>{u.id}</TableCell>
                <TableCell>{u.name}</TableCell>
                <TableCell>{u.email}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={u.role}
                    color={u.role === 'CENTRAL_ADMIN' ? 'primary' : 'default'}
                  />
                </TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    label={u.status}
                    color={u.status === 'ACTIVE' ? 'success' : 'warning'}
                    variant="outlined"
                  />
                </TableCell>
                <TableCell align="right">
                  {u.role === 'HUB_PICKER' && (
                    <Tooltip title="Assign warehouses">
                      <IconButton onClick={() => setMappingUser(u)}>
                        <WarehouseIcon />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title="Edit">
                    <IconButton onClick={() => openEdit(u)}>
                      <EditIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete">
                    <IconButton color="error" onClick={() => handleDelete(u)}>
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {data?.length === 0 && <TableRow><TableCell colSpan={6}>No users yet.</TableCell></TableRow>}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={mode !== null} onClose={() => setMode(null)} fullWidth maxWidth="sm">
        <DialogTitle>{mode === 'create' ? 'New user' : 'Edit user'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              label="Name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              required
            />
            <TextField
              label="Email"
              type="email"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              required
            />
            <TextField
              label={mode === 'create' ? 'Password' : 'New password (leave blank to keep)'}
              type="password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required={mode === 'create'}
              helperText="At least 8 characters"
            />
            <FormControl fullWidth>
              <InputLabel id="role-label">Role</InputLabel>
              <Select
                labelId="role-label"
                label="Role"
                value={form.role}
                onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
              >
                {ROLES.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
              </Select>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel id="status-label">Status</InputLabel>
              <Select
                labelId="status-label"
                label="Status"
                value={form.status}
                onChange={(e) => setForm({ ...form, status: e.target.value as UserStatus })}
              >
                {STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
              </Select>
            </FormControl>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMode(null)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              setFormError(null);
              saveMutation.mutate();
            }}
            disabled={
              saveMutation.isPending ||
              !form.name ||
              !form.email ||
              (mode === 'create' && form.password.length < 8)
            }
          >
            {mode === 'create' ? 'Create' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>

      {mappingUser && (
        <PickerMappingDialog user={mappingUser} onClose={() => setMappingUser(null)} />
      )}
    </>
  );
}

function PickerMappingDialog({ user, onClose }: { user: User; onClose: () => void }) {
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  const [selected, setSelected] = useState<number[]>([]);
  const [result, setResult] = useState<number[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => mappingApi.mapPicker(user.id, selected),
    onSuccess: (res) => setResult(res.warehouseIds),
    onError: (err) => setError(apiErrorMessage(err)),
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Assign warehouses — {user.email}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {result && (
            <Alert severity="success">
              Picker now assigned to warehouse ids: {result.join(', ') || '(none)'}
            </Alert>
          )}
          <FormControl fullWidth>
            <InputLabel id="wh-label">Warehouses to assign</InputLabel>
            <Select
              labelId="wh-label"
              multiple
              value={selected}
              input={<OutlinedInput label="Warehouses to assign" />}
              onChange={(e) =>
                setSelected(typeof e.target.value === 'string' ? [] : (e.target.value as number[]))
              }
              renderValue={(ids) =>
                (ids as number[])
                  .map((id) => warehouses.data?.find((w) => w.id === id)?.warehouseCode ?? id)
                  .join(', ')
              }
            >
              {warehouses.data?.map((w) => (
                <MenuItem key={w.id} value={w.id}>
                  {w.warehouseCode} — {w.warehouseName}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          onClick={() => {
            setError(null);
            mutation.mutate();
          }}
          disabled={mutation.isPending || selected.length === 0}
        >
          Assign
        </Button>
      </DialogActions>
    </Dialog>
  );
}
