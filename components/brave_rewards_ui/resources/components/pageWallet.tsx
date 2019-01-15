/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import { AlertWallet } from 'brave-ui/features/rewards/walletWrapper'
import { ModalAddFunds, ModalBackupRestore, WalletSummary, WalletWrapper } from 'brave-ui/features/rewards'
import { CloseStrokeIcon, WalletAddIcon } from 'brave-ui/components/icons'
import { StyledWalletClose, StyledWalletOverlay, StyledWalletWrapper } from './style'

// Utils
const clipboardCopy = require('clipboard-copy')
import { getLocale } from '../../../common/locale'
import * as rewardsActions from '../actions/rewards_actions'
import * as utils from '../utils'
import { convertProbiToFixed } from '../../../common/probiUtils'

interface State {
  activeTabId: number
  addFundsShown?: boolean
}

interface Props extends Rewards.ComponentProps {
  visible?: boolean
  toggleAction: () => void
}

class PageWallet extends React.Component<Props, State> {
  private backupFileName: string = 'brave_wallet_recovery.txt'

  constructor (props: Props) {
    super(props)

    this.state = {
      activeTabId: 0,
      addFundsShown: false
    }
  }

  get actions () {
    return this.props.actions
  }

  componentDidMount () {
    this.isAddFundsUrl()
  }

  isAddFundsUrl = () => {
    if (window &&
        window.location &&
        window.location.hash &&
        window.location.hash === '#add-funds') {
      this.setState({
        addFundsShown: true
      })
    } else {
      this.setState({
        addFundsShown: false
      })
    }
  }

  notImplemented = () => {
    // Feature not implemented
  }

  toggleAddFunds = () => {
    this.setState({ addFundsShown: !this.state.addFundsShown })
  }

  onModalBackupOpen = () => {
    const { recoveryKey } = this.props.rewardsData
    if (recoveryKey.length === 0) {
      this.actions.getWalletPassphrase()
    }
    this.actions.onModalBackupOpen()
  }

  onModalBackupClose = () => {
    this.actions.onModalBackupClose()
  }

  onModalBackupTabChange = () => {
    const newTabId = this.state.activeTabId === 0 ? 1 : 0
    this.setState({
      activeTabId: newTabId
    })
  }

  onModalBackupOnCopy = (backupKey: string) => {
    const success = clipboardCopy(backupKey)
    console.log(success ? 'Copy successful' : 'Copy failed')
  }

  onModalBackupOnPrint = (backupKey: string) => {
    if (!document.location) {
      return
    }

    const documentWindow = window.open(document.location.href)
    if (documentWindow) {
      documentWindow.document.body.innerText = this.getBackupString(backupKey)
      documentWindow.print()
      documentWindow.close()
    }
  }

  onModalBackupOnSaveFile = (backupKey: string) => {
    const backupString = this.getBackupString(backupKey)
    const blob = new Blob([backupString], { type: 'plain/test' })
    const fileUrl = window.URL.createObjectURL(blob)

    const saveAnchor = document.createElement('a')
    saveAnchor.style.display = 'none'
    saveAnchor.href = fileUrl
    saveAnchor.download = this.backupFileName
    document.body.appendChild(saveAnchor)

    saveAnchor.click()
    window.URL.revokeObjectURL(fileUrl)
    document.body.removeChild(saveAnchor)
  }

  onModalBackupOnRestore = (backupKey: string | MouseEvent) => {
    console.log(`restoring ${backupKey}`)
  }

  pullRecoveryKeyFromFile = (backupKey: string) => {
    console.log(`pulling ${backupKey}`)
  }

  onModalBackupOnImport = () => {
    console.log('To be implemented')
  }

  getBackupString = (key: string) => {
    return utils.constructBackupString(key)
  }

  getConversion = () => {
    const walletInfo = this.props.rewardsData.walletInfo
    return utils.convertBalance(walletInfo.balance.toString(), walletInfo.rates)
  }

  getGrants = () => {
    const grants = this.props.rewardsData.walletInfo.grants
    if (!grants) {
      return []
    }

    return grants.map((grant: Rewards.Grant) => {
      return {
        tokens: convertProbiToFixed(grant.probi),
        expireDate: new Date(grant.expiryTime * 1000).toLocaleDateString()
      }
    })
  }

  walletAlerts = (): AlertWallet | null => {
    const { walletServerProblem } = this.props.rewardsData.ui

    if (walletServerProblem) {
      return {
        node: <React.Fragment><b>{getLocale('uhOh')}</b> {getLocale('serverNotResponding')}</React.Fragment>,
        type: 'error'
      }
    }

    return null
  }

  getWalletSummary = () => {
    const { walletInfo, reports } = this.props.rewardsData
    const { rates } = walletInfo

    let props = {}

    const currentTime = new Date()
    const reportKey = `${currentTime.getFullYear()}_${currentTime.getMonth() + 1}`
    const report: Rewards.Report = reports[reportKey]
    if (report) {
      for (let key in report) {
        const item = report[key]

        if (item.length > 1 && key !== 'total') {
          const tokens = convertProbiToFixed(item)
          props[key] = {
            tokens,
            converted: utils.convertBalance(tokens, rates)
          }
        }
      }
    }

    return {
      report: props
    }
  }

  render () {
    const { visible, toggleAction } = this.props
    const { connectedWallet, addresses, walletInfo, recoveryKey, ui } = this.props.rewardsData
    const { walletRecoverySuccess, modalBackup } = ui
    const { balance } = walletInfo
    const addressArray = utils.getAddresses(addresses)

    if (!visible) {
      return null
    }

    return (
      <React.Fragment>
        <StyledWalletOverlay>
          <StyledWalletClose>
            <CloseStrokeIcon onClick={toggleAction}/>
          </StyledWalletClose>
          <StyledWalletWrapper>
            <WalletWrapper
              balance={balance.toFixed(1)}
              converted={utils.formatConverted(this.getConversion())}
              actions={[
                {
                  name: getLocale('panelAddFunds'),
                  action: this.toggleAddFunds,
                  icon: <WalletAddIcon />
                }
              ]}
              compact={true}
              isMobile={true}
              onSettingsClick={this.onModalBackupOpen}
              onActivityClick={this.notImplemented}
              showSecActions={true}
              grants={this.getGrants()}
              alert={this.walletAlerts()}
              connectedWallet={connectedWallet}
            >
              <WalletSummary {...this.getWalletSummary()}/>
            </WalletWrapper>
            {
              modalBackup
              ? <ModalBackupRestore
                activeTabId={this.state.activeTabId}
                backupKey={recoveryKey}
                onTabChange={this.onModalBackupTabChange}
                onClose={this.onModalBackupClose}
                onCopy={this.onModalBackupOnCopy}
                onPrint={this.onModalBackupOnPrint}
                onSaveFile={this.onModalBackupOnSaveFile}
                onRestore={this.onModalBackupOnRestore}
                error={walletRecoverySuccess === false ? getLocale('walletRecoveryFail') : ''}
                />
              : null
            }
          </StyledWalletWrapper>
          {
            this.state.addFundsShown
            ? <ModalAddFunds isMobile={true} onClose={this.toggleAddFunds} addresses={addressArray} />
            : null
          }
        </StyledWalletOverlay>
      </React.Fragment>
    )
  }
}

const mapStateToProps = (state: Rewards.ApplicationState) => ({
  rewardsData: state.rewardsData
})

const mapDispatchToProps = (dispatch: Dispatch) => ({
  actions: bindActionCreators(rewardsActions, dispatch)
})

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(PageWallet)
