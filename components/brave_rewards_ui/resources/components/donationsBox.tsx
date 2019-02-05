/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import { StyledListContent } from './style'
import { BoxMobile } from 'brave-ui/src/features/rewards/mobile'
import { TableDonation, Tokens, List, NextContribution } from 'brave-ui/src/features/rewards'
import { DetailRow } from 'brave-ui/src/features/rewards/tableDonation'
import { Provider } from 'brave-ui/src/features/rewards/profile'

// Utils
import * as utils from '../utils'
import { getLocale } from '../../../common/locale'
import * as rewardsActions from '../actions/rewards_actions'

interface Props extends Rewards.ComponentProps {
}

class DonationBox extends React.Component<Props, {}> {
  get actions () {
    return this.props.actions
  }

  getTotal = () => {
    const { reports } = this.props.rewardsData

    const currentTime = new Date()
    const reportKey = `${currentTime.getFullYear()}_${currentTime.getMonth() + 1}`
    const report: Rewards.Report = reports[reportKey]

    if (report) {
      return utils.donationTotal(report)
    }

    return '0.0'
  }

  getDonationRows = () => {
    const { walletInfo, recurringList, tipsList } = this.props.rewardsData

    let recurring: DetailRow[] = []
    if (recurringList) {
      recurring = recurringList.map((item: Rewards.Publisher) => {
        let faviconUrl = `chrome://favicon/size/48@1x/${item.url}`
        if (item.favIcon && item.verified) {
          faviconUrl = `chrome://favicon/size/48@1x/${item.favIcon}`
        }

        return {
          profile: {
            name: item.name,
            verified: item.verified,
            provider: (item.provider ? item.provider : undefined) as Provider,
            src: faviconUrl
          },
          contribute: {
            tokens: item.percentage.toFixed(1),
            converted: utils.convertBalance(item.percentage.toString(), walletInfo.rates)
          },
          url: item.url,
          type: 'recurring' as any,
          onRemove: () => { this.actions.removeRecurring(item.id) }
        }
      })
    }

    let tips: DetailRow[] = []
    if (tipsList) {
      tips = tipsList.map((item: Rewards.Publisher) => {
        let faviconUrl = `chrome://favicon/size/48@1x/${item.url}`
        if (item.favIcon && item.verified) {
          faviconUrl = `chrome://favicon/size/48@1x/${item.favIcon}`
        }

        const token = utils.convertProbiToFixed(item.percentage.toString())

        return {
          profile: {
            name: item.name,
            verified: item.verified,
            provider: (item.provider ? item.provider : undefined) as Provider,
            src: faviconUrl
          },
          contribute: {
            tokens: token,
            converted: utils.convertBalance(token, walletInfo.rates)
          },
          url: item.url,
          text: item.tipDate ? new Date(item.tipDate * 1000).toLocaleDateString() : undefined,
          type: 'donation' as any,
          onRemove: () => { this.actions.removeRecurring(item.id) }
        }
      })
    }

    return recurring.concat(tips)
  }

  render () {
    const {
      walletInfo,
      enabledMain,
      recurringList,
      reconcileStamp
    } = this.props.rewardsData
    const donationRows = this.getDonationRows()
    const numRows = donationRows.length
    const allSites = !(numRows > 5)
    const total = this.getTotal()
    const converted = utils.convertBalance(total, walletInfo.rates)

    return (
      <BoxMobile
        title={getLocale('donationTitle')}
        type={'donation'}
        toggle={false}
        checked={enabledMain}
        description={getLocale('donationDesc')}
      >
        <List title={<StyledListContent>{getLocale('donationTotalDonations')}</StyledListContent>}>
          <StyledListContent>
            <Tokens value={total} converted={converted} />
          </StyledListContent>
        </List>
        {
          recurringList && recurringList.length > 0
          ? <List title={getLocale('contributionNextDate')}>
            <StyledListContent>
              <NextContribution>
                {new Date(reconcileStamp * 1000).toLocaleDateString()}
              </NextContribution>
            </StyledListContent>
          </List>
          : null
        }
        <StyledListContent>
          <TableDonation
            rows={donationRows}
            allItems={allSites}
            headerColor={true}
          >
            {getLocale('donationVisitSome')}
          </TableDonation>
        </StyledListContent>
      </BoxMobile>
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
)(DonationBox)
