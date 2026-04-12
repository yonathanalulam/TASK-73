<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { propertyApi, type Property, type RoomType, type PropertyAmenity } from '@/api/property';
import { ApiException } from '@/api/client';
import { useToast } from '@/composables/useToast';

const toast = useToast();
const loading = ref(false);
const properties = ref<Property[]>([]);

// Detail state
const selectedProperty = ref<Property | null>(null);
const roomTypes = ref<RoomType[]>([]);
const amenities = ref<PropertyAmenity[]>([]);
const loadingDetail = ref(false);

async function load() {
  loading.value = true;
  try {
    properties.value = await propertyApi.list();
  } catch (err) {
    toast.error(err instanceof ApiException ? err.apiError.message : 'Failed to load properties');
  } finally {
    loading.value = false;
  }
}

async function selectProperty(p: Property) {
  selectedProperty.value = p;
  loadingDetail.value = true;
  roomTypes.value = [];
  amenities.value = [];
  try {
    const [rt, am] = await Promise.all([
      propertyApi.listRoomTypes(p.id),
      propertyApi.listAmenities(p.id),
    ]);
    roomTypes.value = rt;
    amenities.value = am;
  } catch (err) {
    toast.error('Failed to load property details');
  } finally {
    loadingDetail.value = false;
  }
}

function backToList() {
  selectedProperty.value = null;
}

function formatRate(cents: number): string {
  return '$' + (cents / 100).toFixed(2);
}

onMounted(load);
</script>

<template>
  <section class="property">
    <!-- Property list -->
    <template v-if="!selectedProperty">
      <header>
        <h1>Properties</h1>
        <p class="muted">Lodging properties within your scope. Select a property to view rooms, amenities, and rates.</p>
      </header>

      <div v-if="loading && properties.length === 0" class="placeholder">Loading...</div>

      <div v-else-if="properties.length > 0" class="grid">
        <div v-for="p in properties" :key="p.id" class="card clickable" @click="selectProperty(p)">
          <div class="card-header">
            <h3>{{ p.name }}</h3>
            <span :class="['badge', p.active ? 'badge-ok' : 'badge-off']">
              {{ p.active ? 'Active' : 'Inactive' }}
            </span>
          </div>
          <p class="code">{{ p.code }}</p>
          <p v-if="p.address" class="address">{{ p.address }}</p>
          <p v-if="p.description" class="desc">{{ p.description }}</p>
        </div>
      </div>

      <div v-else class="placeholder">No properties found within your data scope.</div>
    </template>

    <!-- Property detail -->
    <template v-else>
      <header>
        <button type="button" class="back-btn" @click="backToList">&larr; Back to Properties</button>
        <h1>{{ selectedProperty.name }}</h1>
        <span :class="['badge', selectedProperty.active ? 'badge-ok' : 'badge-off']">
          {{ selectedProperty.active ? 'Active' : 'Inactive' }}
        </span>
      </header>

      <div class="detail-section" v-if="selectedProperty.address">
        <strong>Address:</strong> {{ selectedProperty.address }}
      </div>
      <div class="detail-section" v-if="selectedProperty.description">
        <strong>Description:</strong> {{ selectedProperty.description }}
      </div>

      <!-- Policies -->
      <div v-if="selectedProperty.policies" class="detail-section policies-box">
        <h3>House Rules / Policies</h3>
        <p>{{ selectedProperty.policies }}</p>
      </div>

      <!-- Amenities -->
      <div class="detail-section" v-if="amenities.length > 0">
        <h3>Amenities</h3>
        <div class="amenity-grid">
          <div v-for="a in amenities" :key="a.id" class="amenity-chip">
            <span v-if="a.icon" class="amenity-icon">{{ a.icon }}</span>
            {{ a.label }}
          </div>
        </div>
      </div>

      <div v-if="loadingDetail" class="placeholder">Loading details...</div>

      <!-- Room Type Comparison -->
      <div class="detail-section" v-if="roomTypes.length > 0">
        <h3>Room Types</h3>
        <table class="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Code</th>
              <th>Max Occupancy</th>
              <th>Nightly Rate</th>
              <th>Features</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="rt in roomTypes" :key="rt.id">
              <td><strong>{{ rt.name }}</strong></td>
              <td class="mono">{{ rt.code }}</td>
              <td>{{ rt.maxOccupancy }} guests</td>
              <td>{{ formatRate(rt.baseRateCents) }} / night</td>
              <td>{{ rt.features ?? '---' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="roomTypes.some(r => r.description)" class="muted" style="margin-top:8px;">
          <template v-for="rt in roomTypes.filter(r => r.description)" :key="rt.id">
            <strong>{{ rt.name }}:</strong> {{ rt.description }}<br/>
          </template>
        </p>
      </div>

      <div v-if="!loadingDetail && roomTypes.length === 0" class="placeholder">
        No room types configured for this property.
      </div>
    </template>
  </section>
</template>

<style scoped>
.property h1 { margin: 0 0 4px; }
.muted { color: var(--color-text-muted); margin: 0 0 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }
.card { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 16px; box-shadow: var(--shadow); }
.clickable { cursor: pointer; transition: box-shadow 0.15s; }
.clickable:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.12); }
.card-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px; }
.card h3 { margin: 0; font-size: 15px; }
.code { margin: 0 0 4px; font-size: 12px; color: var(--color-text-muted); font-family: monospace; }
.address { margin: 0 0 8px; font-size: 13px; }
.desc { margin: 0 0 8px; font-size: 13px; color: var(--color-text-muted); }
.back-btn { background: none; border: none; cursor: pointer; font-size: 14px; color: var(--color-primary); padding: 0; margin-bottom: 8px; }
.detail-section { margin-bottom: 20px; font-size: 14px; }
.detail-section h3 { font-size: 15px; margin: 0 0 8px; }
.policies-box { background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); padding: 12px; }
.policies-box p { margin: 4px 0 0; white-space: pre-wrap; font-size: 13px; }
.amenity-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.amenity-chip { display: inline-flex; align-items: center; gap: 4px; padding: 4px 12px; background: #e6f6ed; border-radius: 999px; font-size: 13px; font-weight: 500; }
.amenity-icon { font-size: 16px; }
.table { width: 100%; border-collapse: collapse; background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius); overflow: hidden; }
.table th, .table td { text-align: left; padding: 8px 12px; border-bottom: 1px solid var(--color-border); font-size: 13px; }
.table thead th { background: var(--color-bg); font-weight: 600; }
.mono { font-family: monospace; font-size: 12px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; }
.badge-ok { background: #e6f6ed; color: #157a3c; }
.badge-off { background: #fbeaea; color: #a0282a; }
.placeholder { padding: 24px; text-align: center; color: var(--color-text-muted); background: var(--color-surface); border: 1px dashed var(--color-border); border-radius: var(--radius); }
</style>
