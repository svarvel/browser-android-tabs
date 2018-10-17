/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

import * as React from 'react'
import { bindActionCreators, Dispatch } from 'redux'
import { connect } from 'react-redux'

// Components
import {
  StyledListContent,
  StyledSitesNum,
  StyledSupport,
  StyledTotalContent,
  StyledSupportSites,
  StyledSitesLink
} from './style'
import { List, NextContribution, TableContribute, Tokens } from 'brave-ui/features/rewards'
import { BoxMobile } from 'brave-ui/features/rewards/mobile'
import { Column, Grid, Select, ControlWrapper, Checkbox } from 'brave-ui/components'
import { Provider } from 'brave-ui/features/rewards/profile'

// Utils
import { getLocale } from '../../../common/locale'
import * as rewardsActions from '../actions/rewards_actions'
import * as utils from '../utils'

interface State {
  allSitesShown: boolean
}

interface MonthlyChoice {
  tokens: string
  converted: string
}

interface Props extends Rewards.ComponentProps {
  enabledContribute?: boolean
}

class ContributeBox extends React.Component<Props, State> {
  constructor (props: Props) {
    super(props)
    this.state = {
      allSitesShown: false
    }
  }

  getContributeRows = (list: Rewards.Publisher[]) => {
    return list.map((item: Rewards.Publisher) => {
      let name = item.name
      if (item.provider) {
        name = `${name} ${getLocale('on')} ${item.provider}`
      }

      return {
        profile: {
          name,
          verified: item.verified,
          provider: (item.provider ? item.provider : undefined) as Provider,
          src: `chrome://favicon/size/48@1x/${item.url}/`
        },
        url: item.url,
        attention: item.percentage,
        onRemove: () => { this.actions.excludePublisher(item.id) }
      }
    })
  }

  get actions () {
    return this.props.actions
  }

  onRestore = () => {
    this.actions.restorePublishers()
  }

  onToggleContribution = () => {
    this.actions.onSettingSave('enabledContribute', !this.props.rewardsData.enabledContribute)
  }

  onSelectSettingChange = (key: string, value: string) => {
    this.actions.onSettingSave(key, +value)
  }

  onCheckSettingChange = (key: string, selected: boolean) => {
    this.actions.onSettingSave(key, selected)
  }

  onSitesShownToggle = () => {
    this.setState({
      allSitesShown: !this.state.allSitesShown
    })
  }

  contributeSettings = (monthlyList: MonthlyChoice[]) => {
    const {
      contributionMinTime,
      contributionMinVisits,
      contributionNonVerified,
      contributionVideos,
      contributionMonthly,
      enabledMain
    } = this.props.rewardsData

    if (!enabledMain) {
      return null
    }

    return (
      <Grid columns={1} customStyle={{ maxWidth: '270px', margin: '0 auto' }}>
        <Column size={1} customStyle={{ justifyContent: 'center', flexWrap: 'wrap' }}>
          <ControlWrapper text={getLocale('contributionMonthly')}>
            <Select
              title={getLocale('contributionMonthly')}
              onChange={this.onSelectSettingChange.bind(this, 'contributionMonthly')}
              value={(contributionMonthly || '').toString()}
            >
              {
                monthlyList.map((choice: MonthlyChoice) => {
                  return <div key={`choice-setting-${choice.tokens}`} data-value={choice.tokens.toString()}>
                    <Tokens
                      value={choice.tokens}
                      converted={choice.converted}
                    />
                  </div>
                })
              }
            </Select>
          </ControlWrapper>
          <ControlWrapper text={getLocale('contributionMinTime')}>
            <Select
              title={getLocale('contributionMinTime')}
              onChange={this.onSelectSettingChange.bind(this, 'contributionMinTime')}
              value={(contributionMinTime || '').toString()}
            >
              <div data-value='5'>{getLocale('contributionTime5')}</div>
              <div data-value='8'>{getLocale('contributionTime8')}</div>
              <div data-value='60'>{getLocale('contributionTime60')}</div>
            </Select>
          </ControlWrapper>
          <ControlWrapper text={getLocale('contributionMinVisits')}>
            <Select
              title={getLocale('contributionMinVisits')}
              onChange={this.onSelectSettingChange.bind(this, 'contributionMinVisits')}
              value={(contributionMinVisits || '').toString()}
            >
              <div data-value='1'>{getLocale('contributionVisit1')}</div>
              <div data-value='5'>{getLocale('contributionVisit5')}</div>
              <div data-value='10'>{getLocale('contributionVisit10')}</div>
            </Select>
          </ControlWrapper>
          <ControlWrapper text={getLocale('contributionAllowed')}>
            <Checkbox
              value={{
                contributionNonVerified: contributionNonVerified,
                contributionVideos: contributionVideos
              }}
              multiple={true}
              onChange={this.onCheckSettingChange}
            >
              <div data-key='contributionNonVerified'>{getLocale('contributionNonVerified')}</div>
              <div data-key='contributionVideos'>{getLocale('contributionVideos')}</div>
            </Checkbox>
          </ControlWrapper>
        </Column>
      </Grid>
    )
  }

  render () {
    const {
      walletInfo,
      contributionMonthly,
      enabledContribute,
      reconcileStamp,
      autoContributeList
    } = this.props.rewardsData
    const prefix = this.state.allSitesShown ? 'Hide all' : 'Show all'
    const monthlyList: MonthlyChoice[] = utils.generateContributionMonthly(walletInfo.choices, walletInfo.rates)
    const contributeRows = this.getContributeRows(autoContributeList)
    const shownRows = this.state.allSitesShown ? contributeRows : contributeRows.slice(0, 5)
    const numRows = contributeRows && contributeRows.length

    return (
      <BoxMobile
        title={getLocale('contributionTitle')}
        type={'contribute'}
        description={getLocale('contributionDesc')}
        toggle={enabledContribute}
        checked={enabledContribute}
        settingsChild={this.contributeSettings(monthlyList)}
        toggleAction={this.onToggleContribution}
      >
        <List title={<StyledListContent>{getLocale('contributionMonthly')}</StyledListContent>}>
          <StyledListContent>
            <Select
              floating={true}
              title={getLocale('contributionMonthly')}
              onChange={this.onSelectSettingChange.bind(this, 'contributionMonthly')}
              value={(contributionMonthly || '').toString()}
            >
              {
                monthlyList.map((choice: MonthlyChoice) => {
                  return <div key={`choice-${choice.tokens}`} data-value={choice.tokens.toString()}>
                    <Tokens
                      value={choice.tokens}
                      converted={choice.converted}
                    />
                  </div>
                })
              }
            </Select>
          </StyledListContent>
        </List>
        <List title={<StyledListContent>{getLocale('contributionNextDate')}</StyledListContent>}>
          <StyledListContent>
            <NextContribution>{new Date(reconcileStamp * 1000).toLocaleDateString()}</NextContribution>
          </StyledListContent>
        </List>
        <StyledSupport>
          <List title={<StyledSupportSites>{getLocale('contributionSites')}</StyledSupportSites>}>
            <StyledTotalContent>
              {getLocale('total')} &nbsp;<Tokens
                value={numRows.toString()}
                hideText={true}
              />
            </StyledTotalContent>
          </List>
        </StyledSupport>
        <StyledListContent>
          <TableContribute
            header={[
              getLocale('rewardsContributeAttention')
            ]}
            rows={shownRows}
            allSites={true}
            numSites={numRows}
            headerColor={true}
            showRemove={true}
            showRowAmount={true}
          >
            {getLocale('contributionVisitSome')}
          </TableContribute>
        </StyledListContent>
        <StyledSitesNum>
          <StyledSitesLink onClick={this.onSitesShownToggle}>
            {prefix} {numRows} sites
          </StyledSitesLink>
        </StyledSitesNum>
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
)(ContributeBox)
