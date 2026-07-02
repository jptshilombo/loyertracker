import { Pipe, PipeTransform } from '@angular/core';

import { Devise } from '../../core/s02/s02-api.service';
import { formatMoney } from './money-format';

@Pipe({ name: 'moneyFormat', standalone: true, pure: true })
export class MoneyFormatPipe implements PipeTransform {
  transform(montant: number | null | undefined, devise: Devise | null | undefined): string {
    return montant == null || devise == null ? '' : formatMoney(montant, devise);
  }
}
