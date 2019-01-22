/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import { StyledListContent } from './style'
import { BoxMobile } from 'brave-ui/src/features/rewards/mobile'
import { TableDonation, Tokens, List } from 'brave-ui/src/features/rewards'
import { Column, Grid, Checkbox, ControlWrapper } from 'brave-ui/src/components'

// Utils
import { getLocale } from '../../../common/locale'
import * as rewardsActions from '../actions/rewards_actions'

interface Props extends Rewards.ComponentProps {
}

class DonationBox extends React.Component<Props, {}> {
  get actions () {
    return this.props.actions
  }

  onCheckSettingChange = (key: string, selected: boolean) => {
    this.actions.onSettingSave(key, selected)
  }

  donationSettings = () => {
    const {
      donationAbilityYT,
      donationAbilityTwitter,
      enabledMain
    } = this.props.rewardsData

    if (!enabledMain) {
      return null
    }

    return (
      <Grid columns={1} customStyle={{ maxWidth: '270px', margin: '0 auto' }}>
        <Column size={1} customStyle={{ justifyContent: 'center', flexWrap: 'wrap' }}>
          <ControlWrapper text={getLocale('donationAbility')}>
            <Checkbox
              value={{
                donationAbilityYT,
                donationAbilityTwitter
              }}
              multiple={true}
              onChange={this.onCheckSettingChange}
            >
              <div data-key='donationAbilityYT'>{getLocale('donationAbilityYT')}</div>
              <div data-key='donationAbilityTwitter'>{getLocale('donationAbilityTwitter')}</div>
            </Checkbox>
          </ControlWrapper>
        </Column>
      </Grid>
    )
  }

  getDonationRows = () => []

  render () {
    const { rewardsData } = this.props
    const donationRows = this.getDonationRows()
    const numRows = donationRows.length
    const allSites = !(numRows > 5)

    return (
      <BoxMobile
        title={getLocale('donationTitle')}
        type={'donation'}
        toggle={false}
        checked={rewardsData.enabledMain}
        description={getLocale('donationDesc')}
        settingsChild={this.donationSettings()}
      >
        <List title={<StyledListContent>{getLocale('donationTotalDonations')}</StyledListContent>}>
          <StyledListContent>
            <Tokens value={'0.0'} converted={'0.00'} />
          </StyledListContent>
        </List>
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
