/**
 * Government Organization Types
 *
 * Type definitions for government organizations and sync operations.
 */

/**
 * Government branch enum
 */
export type GovernmentBranch = 'executive' | 'legislative' | 'judicial';

/**
 * Government organization entity
 */
export interface GovernmentOrganization {
  id: string;
  officialName: string;
  acronym: string | null;
  orgType: string;
  branch: GovernmentBranch;
  parentId: string | null;
  orgLevel: number;
  establishedDate: string | null;
  dissolvedDate: string | null;
  websiteUrl: string | null;
  jurisdictionAreas: string[] | null;
  active: boolean;
  description?: string | null;
  mission?: string | null;
}

/**
 * Government organization sync status from the backend API
 */
export interface GovOrgSyncStatus {
  lastSync: string | null;
  totalOrganizations: number;
  countByBranch: {
    executive: number;
    legislative: number;
    judicial: number;
  };
  federalRegisterAvailable: boolean;
  maxNewOrgs: number;
}

/**
 * Result of a government organization sync operation
 */
export interface GovOrgSyncResult {
  added: number;
  updated: number;
  skipped: number;
  errors: number;
  errorMessages: string[];
}

/**
 * Validation error from CSV import
 */
export interface CsvValidationError {
  line: number;
  field: string;
  value: string;
  message: string;
}

/**
 * Result of a CSV import operation
 */
export interface CsvImportResult {
  success: boolean;
  added: number;
  updated: number;
  skipped: number;
  errors: number;
  validationErrors: CsvValidationError[];
  errorMessages: string[];
}
