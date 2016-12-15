/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses>.
 */

package es.usc.citius.servando.calendula.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.util.List;

import es.usc.citius.servando.calendula.CalendulaApp;
import es.usc.citius.servando.calendula.R;
import es.usc.citius.servando.calendula.activities.MedicineInfoActivity;
import es.usc.citius.servando.calendula.database.DB;
import es.usc.citius.servando.calendula.drugdb.model.persistence.Prescription;
import es.usc.citius.servando.calendula.events.PersistenceEvents;
import es.usc.citius.servando.calendula.modules.ModuleManager;
import es.usc.citius.servando.calendula.modules.modules.StockModule;
import es.usc.citius.servando.calendula.persistence.Medicine;
import es.usc.citius.servando.calendula.persistence.PatientAlert;
import es.usc.citius.servando.calendula.util.IconUtils;
import es.usc.citius.servando.calendula.util.prospects.ProspectUtils;

/**
 * Created by joseangel.pineiro on 12/2/13.
 */
public class MedicinesListFragment extends Fragment {

    private static final String TAG = "MedicinesListFragment";

    List<Medicine> mMedicines;
    OnMedicineSelectedListener mMedicineSelectedCallback;
    ArrayAdapter adapter;
    ListView listview;
    Handler handler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_medicines_list, container, false);
        handler = new Handler();
        listview = (ListView) rootView.findViewById(R.id.medicines_list);
        View empty = rootView.findViewById(android.R.id.empty);
        listview.setEmptyView(empty);
        mMedicines = DB.medicines().findAllForActivePatient(getContext());
        adapter = new MedicinesListAdapter(getActivity(), R.layout.medicines_list_item, mMedicines);
        listview.setAdapter(adapter);
        return rootView;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void notifyDataChange() {
        Log.d(getTag(), "Medicines - Notify data change");
        new ReloadItemsTask().execute();
    }

    public void openProspect(Prescription p) {
        ProspectUtils.openProspect(p, getActivity(), true);
    }

    public void showDrivingAdvice(final Prescription p) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(getString(R.string.driving_warning))
                .setTitle(getString(R.string.driving_warning_title))
                .setIcon(getResources().getDrawable(R.drawable.ic_warning_amber_48dp));
        builder.setPositiveButton(getString(R.string.driving_warning_show_prospect), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                openProspect(p);

            }
        });
        builder.setNeutralButton(getString(R.string.driving_warning_gotit), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // If the container activity has implemented the callback interface, set it as listener
        if (activity instanceof OnMedicineSelectedListener) {
            mMedicineSelectedCallback = (OnMedicineSelectedListener) activity;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        CalendulaApp.eventBus().register(this);
    }

    @Override
    public void onStop() {
        CalendulaApp.eventBus().unregister(this);
        super.onStop();
    }

    // Method called from the event bus
    @SuppressWarnings("unused")
    public void onEvent(Object evt) {
        if (evt instanceof PersistenceEvents.ActiveUserChangeEvent) {
            notifyDataChange();
        } else if (evt instanceof PersistenceEvents.ModelCreateOrUpdateEvent) {
            if (((PersistenceEvents.ModelCreateOrUpdateEvent) evt).clazz.equals(Medicine.class)) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        notifyDataChange();
                    }
                });
            }
        }
    }

    void openMedicineInfoActivity(Medicine medicine, boolean showAlerts) {
        Intent i = new Intent(getActivity(), MedicineInfoActivity.class);
        i.putExtra("medicine_id", medicine.getId());
        i.putExtra("show_alerts", showAlerts);
        getActivity().startActivity(i);
    }

    void showDeleteConfirmationDialog(final Medicine m) {
        String message;
        if (!DB.schedules().findByMedicine(m).isEmpty()) {
            message = String.format(getString(R.string.remove_medicine_message_long), m.name());
        } else {
            message = String.format(getString(R.string.remove_medicine_message_short), m.name());
        }

        new MaterialStyledDialog.Builder(getActivity())
                .setStyle(Style.HEADER_WITH_ICON)
                .setIcon(IconUtils.icon(getActivity(), CommunityMaterial.Icon.cmd_pill, R.color.white, 100))
                .setHeaderColor(R.color.android_red)
                .withDialogAnimation(true)
                .setTitle(getString(R.string.remove_medicine_dialog_title))
                .setDescription(message)
                .setCancelable(true)
                .setNeutralText(getString(R.string.dialog_no_option))
                .setPositiveText(getString(R.string.dialog_yes_option))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        DB.medicines().deleteCascade(m, true);
                        notifyDataChange();
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.cancel();
                    }
                })
                .show();

    }

    private View createMedicineListItem(LayoutInflater inflater, final Medicine medicine) {

        View item = inflater.inflate(R.layout.medicines_list_item, null);
        ImageView icon = (ImageView) item.findViewById(R.id.imageButton);
        TextView name = (TextView) item.findViewById(R.id.medicines_list_item_name);
        ImageView alertIcon = (ImageView) item.findViewById(R.id.imageView);
        name.setText(medicine.name());
        icon.setImageDrawable(new IconicsDrawable(getContext())
                .icon(medicine.presentation().icon())
                .colorRes(R.color.agenda_item_title)
                .paddingDp(8)
                .sizeDp(40));

        View overlay = item.findViewById(R.id.medicines_list_item_container);
        overlay.setTag(medicine);

        if (ModuleManager.isEnabled(StockModule.ID)) {
            String nextPickup = medicine.nextPickup();
            TextView stockInfo = (TextView) item.findViewById(R.id.stock_info);
            stockInfo.setVisibility(View.VISIBLE);

            if (nextPickup != null) {
                stockInfo.setText("Próxima e-Receta: " + nextPickup);
            }

            if (medicine.stock() >= 0) {
                stockInfo.setText(getString(R.string.stock_remaining_msg, medicine.stock().intValue(), medicine.presentation().units(getResources())));
            }
        }

        String cn = medicine.cn();
        final Prescription p = cn != null ? DB.drugDB().prescriptions().findByCn(medicine.cn()) : null;

        List<PatientAlert> alerts = DB.alerts().findBy(PatientAlert.COLUMN_MEDICINE, medicine);
        boolean hasAlerts = !alerts.isEmpty();

        if (!hasAlerts) {
            item.findViewById(R.id.imageView).setVisibility(View.GONE);
        } else {
            int level = PatientAlert.Level.LOW;
            for (PatientAlert a : alerts) {
                if (a.getLevel() > level) {
                    level = a.getLevel();
                }
            }
            alertIcon.setImageDrawable(IconUtils.alertLevelIcon(level, getActivity()));

            item.findViewById(R.id.imageView).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openMedicineInfoActivity(medicine, true);
                }
            });
        }

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Medicine m = (Medicine) view.getTag();
                if (mMedicineSelectedCallback != null && m != null) {
                    Log.d(getTag(), "Click at " + m.name());
                    mMedicineSelectedCallback.onMedicineSelected(m);
                } else {
                    Log.d(getTag(), "No callback set");
                }
            }
        };

        overlay.setOnClickListener(clickListener);
        overlay.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (view.getTag() != null)
                    showDeleteConfirmationDialog((Medicine) view.getTag());
                return true;
            }
        });
        return item;
    }


    //
    // Container Activity must implement this interface
    //
    public interface OnMedicineSelectedListener {
        void onMedicineSelected(Medicine m);

        void onCreateMedicine();
    }

    private class ReloadItemsTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            mMedicines = DB.medicines().findAllForActivePatient(getContext());

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            adapter.clear();
            for (Medicine m : mMedicines) {
                adapter.add(m);
            }
            adapter.notifyDataSetChanged();
        }
    }

    private class MedicinesListAdapter extends ArrayAdapter<Medicine> {

        public MedicinesListAdapter(Context context, int layoutResourceId, List<Medicine> items) {
            super(context, layoutResourceId, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LayoutInflater layoutInflater = getActivity().getLayoutInflater();
            return createMedicineListItem(layoutInflater, mMedicines.get(position));
        }
    }

}