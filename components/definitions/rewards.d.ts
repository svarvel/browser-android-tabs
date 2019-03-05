declare namespace Rewards {
  export interface ApplicationState {
    rewardsData: State | undefined
  }

  export enum Result {
    OK = 0,
    ERROR = 1,
    NO_PUBLISHER_STATE = 2,
    NO_LEDGER_STATE = 3,
    INVALID_PUBLISHER_STATE = 4,
    INVALID_LEDGER_STATE = 5,
    CAPTCHA_FAILED = 6
  }

  export type AddressesType = 'BTC' | 'ETH' | 'BAT' | 'LTC'
  export type Address = { address: string, qr: string | null }

  export interface State {
    addresses?: Record<AddressesType, Address>
    adsData: AdsData
    currentGrant?: Grant
    createdTimestamp: number | null
    enabledMain: boolean
    enabledContribute: boolean
    firstLoad: boolean | null
    walletCreated: boolean
    walletCreateFailed: boolean
    contributionMinTime: number
    contributionMinVisits: number
    contributionMonthly: number
    contributionNonVerified: boolean
    contributionVideos: boolean
    donationAbilityYT: boolean
    donationAbilityTwitter: boolean
    excluded: string[]
    numExcludedSites: number
    walletInfo: WalletProperties
    connectedWallet: boolean
    recoveryKey: string
    grants?: Grant[]
    reconcileStamp: number
    reports: Record<string, Report>
    ui: {
      walletRecoverySuccess: boolean | null
      emptyWallet: boolean
      walletServerProblem: boolean
      modalBackup: boolean
    }
    autoContributeList: Publisher[],
    safetyNetFailed: boolean
    recurringList: Publisher[]
    tipsList: Publisher[]
    tipsLoad: boolean
    recurringLoad: boolean
    pendingContributionTotal: number
  }

  export interface ComponentProps {
    rewardsData: State
    actions: any
  }

  export type GrantStatus = 'wrongPosition' | 'grantGone' | 'generalError' | 'grantAlreadyClaimed' | number | null

  export interface GrantResponse {
    promotionId?: string
    status?: number
    type?: string
  }

  export interface Grant {
    promotionId?: string
    altcurrency?: string
    probi: string
    expiryTime: number
    captcha?: string
    hint?: string
    status?: GrantStatus
    type?: string
  }

  export interface WalletProperties {
    balance: number
    choices: number[]
    probi: string
    range?: number[]
    rates?: Record<string, number>
    grants?: Grant[]
  }

  export interface RecoverWallet {
    result: Result
    balance: number
    grants?: Grant[]
  }

  export interface GrantFinish {
    result: Result,
    probi: string,
    status: number,
    expiryTime: number
  }

  export enum Status {
    DEFAULT = 0,
    EXCLUDED = 1,
    INCLUDED = 2
  }

  export interface Publisher {
    publisherKey: string
    percentage: number
    verified: boolean
    excluded: Status
    url: string
    name: string
    provider: string
    favIcon: string
    id: string
    tipDate?: number
  }

  export interface Report {
    ads: string
    closing: string
    contribute: string
    deposit: string
    donation: string
    grant: string
    tips: string
    opening: string
    total: string
  }

  export interface Captcha {
    image: string
    hint: string
  }

  export interface AdsData {
    adsEnabled: boolean
    adsPerHour: number
    adsUIEnabled: boolean
  }
}
